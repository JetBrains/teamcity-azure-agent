/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.utils;

import java.text.DecimalFormatSymbols;
import java.util.Comparator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares Strings by human values instead of traditional machine values.
 *
 * @author elberry
 */
public class AlphaNumericStringComparator implements Comparator<String> {

    private Pattern alphaNumChunkPattern;

    public AlphaNumericStringComparator() {
        this(Locale.getDefault());
    }

    public AlphaNumericStringComparator(Locale locale) {
        DecimalFormatSymbols dfs = new DecimalFormatSymbols(locale);
        char localeDecimalSeparator = dfs.getDecimalSeparator();
        // alphaNumChunkPatter initialized here to get correct decimal separator for locale.
        alphaNumChunkPattern = Pattern.compile("(\\d+\\" + localeDecimalSeparator + "\\d+)|(\\d+)|(\\D+)");
    }

    public int compare(String s1, String s2) {
        int compareValue = 0;
        Matcher s1ChunkMatcher = alphaNumChunkPattern.matcher(s1);
        Matcher s2ChunkMatcher = alphaNumChunkPattern.matcher(s2);
        String s1ChunkValue = null;
        String s2ChunkValue = null;
        while (s1ChunkMatcher.find() && s2ChunkMatcher.find() && compareValue == 0) {
            s1ChunkValue = s1ChunkMatcher.group();
            s2ChunkValue = s2ChunkMatcher.group();
            try {
                // compare double values - ints get converted to doubles. Eg. 100 = 100.0
                Double s1Double = Double.valueOf(s1ChunkValue);
                Double s2Double = Double.valueOf(s2ChunkValue);
                compareValue = s1Double.compareTo(s2Double);
            } catch (NumberFormatException e) {
                // not a number, use string comparison.
                compareValue = s1ChunkValue.compareTo(s2ChunkValue);
            }
            // if they are equal thus far, but one has more left, it should come after the one that doesn't.
            if (compareValue == 0) {
                if (s1ChunkMatcher.hitEnd() && !s2ChunkMatcher.hitEnd()) {
                    compareValue = -1;
                } else if (!s1ChunkMatcher.hitEnd() && s2ChunkMatcher.hitEnd()) {
                    compareValue = 1;
                }
            }
        }
        return compareValue;
    }
}
