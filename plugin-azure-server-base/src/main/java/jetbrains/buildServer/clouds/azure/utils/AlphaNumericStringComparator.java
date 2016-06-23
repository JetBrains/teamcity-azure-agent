/*
 * Copyright (c) 2007 Eric Berry <elberry@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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