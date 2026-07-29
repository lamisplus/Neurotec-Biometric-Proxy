package org.lamisplus.biometric.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ReaderMatcher {

    private static final String INSTANCE_SUFFIX = "\\s*#\\s*\\d+$";

    private ReaderMatcher() {
    }

    public static final class Candidate {
        private final String displayName;
        private final String make;
        private final String model;
        private final String id;

        public Candidate(String displayName, String make, String model, String id) {
            this.displayName = displayName;
            this.make = make;
            this.model = model;
            this.id = id;
        }

        private String description() {
            return normalise(String.format("%s %s %s", displayName, make, model));
        }

        @Override
        public String toString() {
            return String.format("%s [make=%s, model=%s, id=%s]", displayName, make, model, id);
        }
    }

    /** How a reader name was matched, for the log line that follows. */
    public enum Strategy {
        EXACT_NAME("exact name"),
        NAME_WITHOUT_INSTANCE_SUFFIX("name without instance suffix"),
        MAKE_AND_MODEL("make/model"),
        VENDOR_ONLY("vendor");

        private final String description;

        Strategy(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public static final class Match {
        private final int index;
        private final Strategy strategy;

        private Match(int index, Strategy strategy) {
            this.index = index;
            this.strategy = strategy;
        }

        public int getIndex() {
            return index;
        }

        public Strategy getStrategy() {
            return strategy;
        }
    }

    public static Match match(String reader, List<Candidate> candidates) {
        String wanted = normalise(decode(reader));
        if (wanted.isEmpty() || candidates.isEmpty()) {
            return null;
        }

        // Exact match on what the SDK reports.
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (wanted.equals(normalise(candidate.displayName)) || wanted.equals(normalise(candidate.id))) {
                return new Match(i, Strategy.EXACT_NAME);
            }
        }
        String wantedBase = stripInstanceSuffix(wanted);
        for (int i = 0; i < candidates.size(); i++) {
            if (wantedBase.equals(stripInstanceSuffix(normalise(candidates.get(i).displayName)))) {
                return new Match(i, Strategy.NAME_WITHOUT_INSTANCE_SUFFIX);
            }
        }

        List<String> words = words(wantedBase);
        if (words.isEmpty()) {
            return null;
        }

        for (int i = 0; i < candidates.size(); i++) {
            if (mentionsAll(candidates.get(i).description(), words)) {
                return new Match(i, Strategy.MAKE_AND_MODEL);
            }
        }

        String vendor = words.get(0);
        int onlyMatch = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).description().contains(vendor)) {
                if (onlyMatch != -1) {
                    return null;
                }
                onlyMatch = i;
            }
        }
        return onlyMatch == -1 ? null : new Match(onlyMatch, Strategy.VENDOR_ONLY);
    }

    public static String decode(String reader) {
        if (reader == null) {
            return "";
        }
        try {
            return URLDecoder.decode(reader, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return reader;
        }
    }

    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String stripInstanceSuffix(String value) {
        return value.replaceAll(INSTANCE_SUFFIX, "").trim();
    }

    private static List<String> words(String value) {
        List<String> words = new ArrayList<>();
        for (String word : value.split("[^a-z0-9]+")) {
            if (word.length() > 1) {
                words.add(word);
            }
        }
        return words;
    }

    private static boolean mentionsAll(String description, List<String> words) {
        for (String word : words) {
            if (!description.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
