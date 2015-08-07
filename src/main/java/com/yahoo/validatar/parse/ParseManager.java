/*
 * Copyright 2014-2015 Yahoo! Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yahoo.validatar.parse;

import com.yahoo.validatar.common.Query;
import com.yahoo.validatar.common.TestSuite;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.log4j.Logger;
import org.reflections.Reflections;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseManager implements FileLoadable {

    public static final Pattern REGEX = Pattern.compile("\\$\\{(.*?)\\}");

    protected Logger log = Logger.getLogger(getClass().getName());

    private HashMap<String, Parser> availableParsers;

    /**
     * Constructor. Default.
     */
    public ParseManager() {
        availableParsers = new HashMap<>();

        Reflections reflections = new Reflections("com.yahoo.validatar.parse");
        Set<Class<? extends Parser>> subTypes = reflections.getSubTypesOf(Parser.class);
        for (Class<? extends Parser> parserClass : subTypes) {
            try {
                Parser parser = parserClass.newInstance();
                availableParsers.put(parser.getName(), parser);
                log.info("Setup parser " + parser.getName());
            } catch (InstantiationException e) {
                log.info("Error instantiating " + parserClass + " " + e);
            } catch (IllegalAccessException e) {
                log.info("Illegal access of " + parserClass + " " + e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TestSuite> load(File path) throws FileNotFoundException {
        List<TestSuite> testSuites = new ArrayList<>();
        if (path == null) {
            return testSuites;
        }

        if (path.isFile()) {
            log.info("TestSuite parameter is a file, loading...");
            CollectionUtils.addIgnoreNull(testSuites, getTestSuite(path));
        } else {
            log.info("TestSuite parameter is a folder, loading all files inside...");
            File[] files = path.listFiles();
            Arrays.sort(files);
            for (File file : files) {
                CollectionUtils.addIgnoreNull(testSuites, getTestSuite(file));
            }
        }
        return testSuites;
    }

    /**
     * Takes a List of non null TestSuite and a map and replaces all the variables in the query with the value
     * in the map, in place.
     *
     * @param suites       A list of TestSuites containing parametrized queries.
     * @param parameterMap A map of parameters to their values.
     * @return The list of TestSuite with replaced queries for chainability.
     */
    public static List<TestSuite> expandParameters(List<TestSuite> suites, Map<String, String> parameterMap) {
        for (TestSuite suite : suites) {
            expandParameters(suite, parameterMap);
        }
        return suites;
    }

    /**
     * Takes a non null TestSuite and a map and replaces all the variables in the query with the value
     * in the map, in place.
     *
     * @param suite        A TestSuite containing parametrized queries.
     * @param parameterMap A map of parameters to their values.
     */
    public static void expandParameters(TestSuite suite, Map<String, String> parameterMap) {
        if (parameterMap == null) {
            return;
        }
        for (Query query : suite.queries) {
            Matcher matcher = REGEX.matcher(query.value);
            StringBuffer newQuery = new StringBuffer();
            while (matcher.find()) {
                String parameterValue = parameterMap.get(matcher.group(1));
                if (parameterValue != null) {
                    matcher.appendReplacement(newQuery, parameterValue);
                }
            }
            matcher.appendTail(newQuery);
            query.value = newQuery.toString();
        }
    }

    /**
     * Takes a non-null File and parses a TestSuite out of it.
     *
     * @param path A File object representing the file.
     * @return The parsed TestSuite from the file. Null if it cannot be parsed.
     * @throws java.io.FileNotFoundException if any.
     */
    protected TestSuite getTestSuite(File path) throws FileNotFoundException {
        TestSuite suite = null;
        if (path.isFile()) {
            Parser parser = availableParsers.get(getFileExtension(path.getName()));
            if (parser == null) {
                log.error("Unable to parse " + path + ". File extension does not match any known parsers. Skipping...");
            } else {
                suite = parser.parse(new FileInputStream(path));
            }
        }
        return suite;
    }

    private String getFileExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return (index > 0) ? fileName.substring(index + 1) : null;
    }
}
