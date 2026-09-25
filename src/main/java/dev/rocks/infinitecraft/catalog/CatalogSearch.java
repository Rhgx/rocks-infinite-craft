package dev.rocks.infinitecraft.catalog;

import dev.rocks.infinitecraft.core.CatalogEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable per-snapshot search data; no game classes are retained. */
public final class CatalogSearch {
    // Removes a leading Minecraft namespace such as "minecraft:" before tokenizing an ID.
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+:");
    // Splits searchable text anywhere that is not a lowercase letter or digit.
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private CatalogSearch() {}
    private record Candidate(CatalogEntry entry, Set<String> names, Set<String> tags, Set<String> all) {}
    private record Ranked(Candidate candidate, double relevance, double tie) {}

    public static List<CatalogEntry> candidates(List<CatalogEntry> catalog, String first, String second, int limit) {
        return new Index(catalog).candidates(first, second, limit);
    }

    /** Build once after catalog reload and reuse for every combination. */
    public static final class Index {
        private final List<Candidate> entries;
        private final Map<String, Candidate> byId;
        private final Map<String, Double> weights;

        public Index(List<CatalogEntry> catalog) {
            entries = catalog.stream().filter(entry -> entry.kind().equals("item") && entry.craftable())
                    .map(entry -> {
                        Set<String> names = terms(entry.id() + " " + entry.name());
                        Set<String> tags = terms(String.join(" ", entry.tags()));
                        Set<String> all = new HashSet<>(names);
                        all.addAll(tags);
                        return new Candidate(entry, Set.copyOf(names), Set.copyOf(tags), Set.copyOf(all));
                    }).sorted(Comparator.comparing(candidate -> candidate.entry.id())).toList();
            Map<String, Candidate> ids = new HashMap<>();
            Map<String, Integer> frequency = new HashMap<>();
            for (Candidate candidate : entries) {
                ids.put(candidate.entry.id(), candidate);
                for (String term : candidate.all) frequency.merge(term, 1, Integer::sum);
            }
            byId = Map.copyOf(ids);
            Map<String, Double> preparedWeights = new HashMap<>();
            frequency.forEach((term, count) -> preparedWeights.put(term, Math.log1p((double) entries.size() / (1 + count))));
            weights = Map.copyOf(preparedWeights);
        }

        public List<CatalogEntry> candidates(String first, String second, int limit) {
            return candidates(first, second, limit, byId.keySet());
        }

        public List<CatalogEntry> candidates(String first, String second, int limit, Set<String> allowedOutputs) {
            return candidates(first, second, limit, allowedOutputs, 50);
        }

        public List<CatalogEntry> candidates(String first, String second, int limit, Set<String> allowedOutputs, int silliness) {
            if (limit <= 0 || entries.isEmpty()) return List.of();
            Set<String> firstTerms = byId.containsKey(first) ? byId.get(first).all : terms(first);
            Set<String> secondTerms = byId.containsKey(second) ? byId.get(second).all : terms(second);
            String pair = first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
            List<Ranked> ranked = new ArrayList<>(entries.size());
            for (Candidate candidate : entries) {
                if (!allowedOutputs.contains(candidate.entry.id())) continue;
                double a = score(candidate, firstTerms);
                double b = score(candidate, secondTerms);
                double relevance = a + b + 2 * Math.min(a, b);
                if (candidate.entry.id().equals(first) || candidate.entry.id().equals(second)) relevance *= .4;
                // Mix a pair-seeded hash so exploration is stable without favoring alphabetically early IDs.
                int hash = (pair + "|" + candidate.entry.id()).hashCode();
                hash = (hash ^ (hash >>> 16)) * 0x85ebca6b;
                hash = (hash ^ (hash >>> 13)) * 0xc2b2ae35;
                double tie = Integer.toUnsignedLong(hash ^ (hash >>> 16)) / 4294967296.0;
                ranked.add(new Ranked(candidate, relevance, tie));
            }
            if (ranked.isEmpty()) return List.of();
            int count = Math.min(limit, ranked.size());
            ranked.sort(Comparator.comparingDouble(Ranked::relevance).reversed()
                    .thenComparing(Comparator.comparingDouble(Ranked::tie).reversed()));
            boolean noMatches = ranked.getFirst().relevance == 0;
            // Bound the quadratic diversity step to at most four times the requested output size.
            Map<String, Ranked> shortlist = new LinkedHashMap<>();
            ranked.stream().limit(Math.min((long) count * 3, ranked.size()))
                    .forEach(candidate -> shortlist.put(candidate.candidate.entry.id(), candidate));
            ranked.sort(Comparator.comparingDouble(Ranked::tie).reversed());
            ranked.stream().limit(count).forEach(candidate -> shortlist.put(candidate.candidate.entry.id(), candidate));
            List<Ranked> pool = List.copyOf(shortlist.values());
            double[] similarity = new double[pool.size()];
            boolean[] selected = new boolean[pool.size()];
            double explorationShare = .1 + .3 * Math.clamp(silliness, 0, 100) / 100.0;
            int exploration = noMatches ? count : count >= 4 ? Math.max(1, (int) Math.round(count * explorationShare)) : 0;
            List<CatalogEntry> result = new ArrayList<>(count);
            // Lexical matching misses semantic connections, so the model also gets a small diverse sample.
            for (int slot = 0; slot < count; slot++) {
                int best = -1;
                double bestScore = -1;
                for (int index = 0; index < pool.size(); index++) {
                    if (selected[index]) continue;
                    Ranked candidate = pool.get(index);
                    double score = slot < count - exploration
                            ? candidate.relevance / (1 + 3 * similarity[index]) + candidate.tie * .000001
                            : (1 - similarity[index]) + candidate.tie * .2;
                    if (score > bestScore) { best = index; bestScore = score; }
                }
                selected[best] = true;
                Candidate chosen = pool.get(best).candidate;
                result.add(chosen.entry);
                for (int index = 0; index < pool.size(); index++) {
                    if (!selected[index]) similarity[index] = Math.max(similarity[index], overlap(chosen.all, pool.get(index).candidate.all));
                }
            }
            return List.copyOf(result);
        }

        private double score(Candidate candidate, Set<String> query) {
            double result = 0;
            for (String term : query) result += weights.getOrDefault(term, 0.0)
                    * ((candidate.names.contains(term) ? 3 : 0) + (candidate.tags.contains(term) ? 1 : 0));
            return result;
        }
    }

    private static double overlap(Set<String> first, Set<String> second) {
        int common = 0;
        for (String term : first) if (second.contains(term)) common++;
        int union = first.size() + second.size() - common;
        return union == 0 ? 0 : (double) common / union;
    }

    private static Set<String> terms(String value) {
        String paths = NAMESPACE.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
        Set<String> words = new HashSet<>(Arrays.asList(WORD_SEPARATOR.split(paths)));
        words.removeAll(Set.of("", "item", "items", "block", "blocks"));
        return words;
    }
}
