/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package com.amazon.dataprepper.plugins.prepper.grok;


import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import io.krakens.grok.api.Match;
import com.amazon.dataprepper.model.record.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class GrokPrepperTests {
    private GrokPrepper grokPrepper;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {};
    private String messageInput;

    @Mock
    private GrokCompiler grokCompiler;

    @Mock
    private Match match;

    @Mock
    private Grok grok;

    @Mock
    private Grok grokSecondMatch;

    private final String PLUGIN_NAME = "grok";
    private Map<String, Object> capture;
    private final Map<String, List<String>> matchConfig = new HashMap<>();

    @BeforeEach
    public void setup() {
        final List<String> matchPatterns = new ArrayList<>();
        matchPatterns.add("%{PATTERN1}");
        matchPatterns.add("%{PATTERN2}");
        matchConfig.put("message", matchPatterns);
    }

    @AfterEach
    public void tearDown() {
      grokPrepper.shutdown();
    }

    private void initialize(final PluginSetting pluginSetting) {
        pluginSetting.setPipelineName("grokPipeline");

        try (MockedStatic<GrokCompiler> grokCompilerMockedStatic = mockStatic(GrokCompiler.class)) {
            grokCompilerMockedStatic.when(GrokCompiler::newInstance).thenReturn(grokCompiler);
            when(grokCompiler.compile(eq(matchConfig.get("message").get(0)), anyBoolean())).thenReturn(grok);
            when(grokCompiler.compile(eq(matchConfig.get("message").get(1)), anyBoolean())).thenReturn(grokSecondMatch);
            grokPrepper = new GrokPrepper(pluginSetting);
        }

        capture = new HashMap<>();

        messageInput = UUID.randomUUID().toString();

        when(grok.match(messageInput)).thenReturn(match);
        when(match.capture()).thenReturn(capture);
    }


    @Test
    public void testMatchMerge() throws JsonProcessingException {
        initialize(getDefaultPluginSetting());

        capture.put("key_capture_1", "value_capture_1");
        capture.put("key_capture_2", "value_capture_2");
        capture.put("key_capture_3", "value_capture_3");

        String testData = "{\"message\":" + "\"" + messageInput + "\"}";
        Record<String> record = new Record<>(testData);

        String resultData = "{\"message\":" + "\"" + messageInput + "\","
                                .concat("\"key_capture_1\":\"value_capture_1\",")
                                .concat("\"key_capture_2\":\"value_capture_2\",")
                                .concat("\"key_capture_3\":\"value_capture_3\"}");

        Record<String> resultRecord = new Record<>(resultData);

        List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

        assertThat(grokkedRecords.size(), equalTo(1));
        assertThat(grokkedRecords.get(0), notNullValue());
        assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
    }

    @Test
    public void testTarget() throws JsonProcessingException {
        final PluginSetting pluginSetting = getDefaultPluginSetting();
        pluginSetting.getSettings().put(GrokPrepperConfig.TARGET_KEY, "test_target");
        initialize(pluginSetting);


        capture.put("key_capture_1", "value_capture_1");
        capture.put("key_capture_2", "value_capture_2");
        capture.put("key_capture_3", "value_capture_3");

        String testData = "{\"message\":" + "\"" + messageInput + "\"}";
        Record<String> record = new Record<>(testData);

        String resultData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"test_target\":{")
                .concat("\"key_capture_1\":\"value_capture_1\",")
                .concat("\"key_capture_2\":\"value_capture_2\",")
                .concat("\"key_capture_3\":\"value_capture_3\"}}");

        Record<String> resultRecord = new Record<>(resultData);

        List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

        assertThat(grokkedRecords.size(), equalTo(1));
        assertThat(grokkedRecords.get(0), notNullValue());
        assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
    }

    @Test
    public void testOverwrite() throws JsonProcessingException {
        final PluginSetting pluginSetting = getDefaultPluginSetting();
        pluginSetting.getSettings().put(GrokPrepperConfig.KEYS_TO_OVERWRITE, Collections.singletonList("message"));
        initialize(pluginSetting);

        capture.put("key_capture_1", "value_capture_1");
        capture.put("key_capture_2", "value_capture_2");
        capture.put("key_capture_3", "value_capture_3");
        capture.put("message", "overwrite_the_original_message");

        String testData = "{\"message\":" + "\"" + messageInput + "\"}";
        Record<String> record = new Record<>(testData);

        String resultData = "{\"message\":\"overwrite_the_original_message\","
                .concat("\"key_capture_1\":\"value_capture_1\",")
                .concat("\"key_capture_2\":\"value_capture_2\",")
                .concat("\"key_capture_3\":\"value_capture_3\"}");

        Record<String> resultRecord = new Record<>(resultData);

        List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

        assertThat(grokkedRecords.size(), equalTo(1));
        assertThat(grokkedRecords.get(0), notNullValue());
        assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
    }

    @Test
    public void testMatchMergeCollisionStrings() throws JsonProcessingException {
        initialize(getDefaultPluginSetting());

        capture.put("key_capture_1", "value_capture_1");
        capture.put("key_capture_2", "value_capture_2");
        capture.put("key_capture_3", "value_capture_3");

        String testData =  "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\":\"value_capture_collision\"}");

        Record<String> record = new Record<>(testData);

        String resultData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\":[\"value_capture_collision\",")
                .concat("\"value_capture_1\"],")
                .concat("\"key_capture_2\":\"value_capture_2\",")
                .concat("\"key_capture_3\":\"value_capture_3\"}");

        Record<String> resultRecord = new Record<>(resultData);

        List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

        assertThat(grokkedRecords.size(), equalTo(1));
        assertThat(grokkedRecords.get(0), notNullValue());
        assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
    }

    @Test
    public void testMatchMergeCollisionInts() throws JsonProcessingException {
        initialize(getDefaultPluginSetting());

        capture.put("key_capture_1", 20);
        capture.put("key_capture_2", "value_capture_2");
        capture.put("key_capture_3", "value_capture_3");

        String testData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\": 10}");

        Record<String> record = new Record<>(testData);

        String resultData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\":[ 10,")
                .concat("20 ],")
                .concat("\"key_capture_2\":\"value_capture_2\",")
                .concat("\"key_capture_3\":\"value_capture_3\"}");

        Record<String> resultRecord = new Record<>(resultData);

        List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

        assertThat(grokkedRecords.size(), equalTo(1));
        assertThat(grokkedRecords.get(0), notNullValue());
        assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
    }

    @Test
    public void testMatchMergeCollisionWithListMixedTypes() throws JsonProcessingException {
        initialize(getDefaultPluginSetting());

        List<Object> captureListValues = new ArrayList<>();
        captureListValues.add("30");
        captureListValues.add(40);
        captureListValues.add(null);

        capture.put("key_capture_1", captureListValues);
        capture.put("key_capture_2", "value_capture_2");
        capture.put("key_capture_3", "value_capture_3");

        String testData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\":[10,\"20\"]}");

        Record<String> record = new Record<>(testData);

        String resultData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\":[10,")
                .concat("\"20\",\"30\",40,null],")
                .concat("\"key_capture_2\":\"value_capture_2\",")
                .concat("\"key_capture_3\":\"value_capture_3\"}");

        Record<String> resultRecord = new Record<>(resultData);

        List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

        assertThat(grokkedRecords.size(), equalTo(1));
        assertThat(grokkedRecords.get(0), notNullValue());
        assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
    }

    @Test
    public void testMatchMergeCollisionWithNullValue() throws JsonProcessingException {
        initialize(getDefaultPluginSetting());

        capture.put("key_capture_1", "value_capture_1");
        capture.put("key_capture_2", "value_capture_2");
        capture.put("key_capture_3", "value_capture_3");

        String testData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\":null}");

        Record<String> record = new Record<>(testData);

        String resultData = "{\"message\":" + "\"" + messageInput + "\","
                .concat("\"key_capture_1\":[null,")
                .concat("\"value_capture_1\"],")
                .concat("\"key_capture_2\":\"value_capture_2\",")
                .concat("\"key_capture_3\":\"value_capture_3\"}");

        Record<String> resultRecord = new Record<>(resultData);

        List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

        assertThat(grokkedRecords.size(), equalTo(1));
        assertThat(grokkedRecords.get(0), notNullValue());
        assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
    }

    @Nested
    class WithMultipleMatches {
        @Mock
        private Match secondMatch;

        private Map<String, Object> secondCapture;

        @BeforeEach
        public void setup() {
            secondCapture = new HashMap<>();
        }

        @Test
        public void testNoCaptures() throws JsonProcessingException {
            initialize(getDefaultPluginSetting());

            lenient().when(grokSecondMatch.match(messageInput)).thenReturn(secondMatch);
            lenient().when(secondMatch.capture()).thenReturn(secondCapture);

            String testData = "{\"message\":" + "\"" + messageInput + "\"}";
            Record<String> record = new Record<>(testData);

            List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

            assertThat(grokkedRecords.size(), equalTo(1));
            assertThat(grokkedRecords.get(0), notNullValue());
            assertThat(equalRecords(grokkedRecords.get(0), record), equalTo(true));
        }

        @Test
        public void testBreakOnMatchTrue() throws JsonProcessingException {
            initialize(getDefaultPluginSetting());

            lenient().when(grokSecondMatch.match(messageInput)).thenReturn(secondMatch);
            lenient().when(secondMatch.capture()).thenReturn(secondCapture);

            capture.put("key_capture_1", "value_capture_1");
            capture.put("key_capture_2", "value_capture_2");
            capture.put("key_capture_3", "value_capture_3");

            secondCapture.put("key_secondCapture", "value_capture2");

            String testData = "{\"message\":" + "\"" + messageInput + "\"}";
            Record<String> record = new Record<>(testData);

            String resultData = "{\"message\":" + "\"" + messageInput + "\","
                    .concat("\"key_capture_1\":\"value_capture_1\",")
                    .concat("\"key_capture_2\":\"value_capture_2\",")
                    .concat("\"key_capture_3\":\"value_capture_3\"}");

            Record<String> resultRecord = new Record<>(resultData);

            List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

            verifyNoInteractions(grokSecondMatch, secondMatch);
            assertThat(grokkedRecords.size(), equalTo(1));
            assertThat(grokkedRecords.get(0), notNullValue());
            assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
        }

        @Test
        public void testBreakOnMatchFalse() throws JsonProcessingException {
            final PluginSetting pluginSetting = getDefaultPluginSetting();
            pluginSetting.getSettings().put(GrokPrepperConfig.BREAK_ON_MATCH, false);
            initialize(pluginSetting);

            when(grokSecondMatch.match(messageInput)).thenReturn(secondMatch);
            when(secondMatch.capture()).thenReturn(secondCapture);

            capture.put("key_capture_1", "value_capture_1");
            capture.put("key_capture_2", "value_capture_2");
            capture.put("key_capture_3", "value_capture_3");

            secondCapture.put("key_secondCapture", "value_secondCapture");

            String testData = "{\"message\":" + "\"" + messageInput + "\"}";
            Record<String> record = new Record<>(testData);

            String resultData = "{\"message\":" + "\"" + messageInput + "\","
                    .concat("\"key_capture_1\":\"value_capture_1\",")
                    .concat("\"key_capture_2\":\"value_capture_2\",")
                    .concat("\"key_secondCapture\":\"value_secondCapture\",")
                    .concat("\"key_capture_3\":\"value_capture_3\"}");

            Record<String> resultRecord = new Record<>(resultData);

            List<Record<String>> grokkedRecords = (List<Record<String>>) grokPrepper.doExecute(Collections.singletonList(record));

            assertThat(grokkedRecords.size(), equalTo(1));
            assertThat(grokkedRecords.get(0), notNullValue());
            assertThat(equalRecords(grokkedRecords.get(0), resultRecord), equalTo(true));
        }
    }

    private PluginSetting getDefaultPluginSetting() {

        return completePluginSettingForGrokPrepper(
                GrokPrepperConfig.DEFAULT_BREAK_ON_MATCH,
                GrokPrepperConfig.DEFAULT_KEEP_EMPTY_CAPTURES,
                matchConfig,
                GrokPrepperConfig.DEFAULT_NAMED_CAPTURES_ONLY,
                Collections.emptyList(),
                Collections.emptyList(),
                GrokPrepperConfig.DEFAULT_PATTERNS_FILES_GLOB,
                Collections.emptyMap(),
                GrokPrepperConfig.DEFAULT_TIMEOUT_MILLIS,
                GrokPrepperConfig.DEFAULT_TARGET_KEY);
    }

    private PluginSetting completePluginSettingForGrokPrepper(final boolean breakOnMatch,
                                                              final boolean keepEmptyCaptures,
                                                              final Map<String, List<String>> match,
                                                              final boolean namedCapturesOnly,
                                                              final List<String> keysToOverwrite,
                                                              final List<String> patternsDirectories,
                                                              final String patternsFilesGlob,
                                                              final Map<String, String> patternDefinitions,
                                                              final int timeoutMillis,
                                                              final String targetKey) {
        final Map<String, Object> settings = new HashMap<>();
        settings.put(GrokPrepperConfig.BREAK_ON_MATCH, breakOnMatch);
        settings.put(GrokPrepperConfig.NAMED_CAPTURES_ONLY, namedCapturesOnly);
        settings.put(GrokPrepperConfig.MATCH, match);
        settings.put(GrokPrepperConfig.KEEP_EMPTY_CAPTURES, keepEmptyCaptures);
        settings.put(GrokPrepperConfig.KEYS_TO_OVERWRITE, keysToOverwrite);
        settings.put(GrokPrepperConfig.PATTERNS_DIRECTORIES, patternsDirectories);
        settings.put(GrokPrepperConfig.PATTERN_DEFINITIONS, patternDefinitions);
        settings.put(GrokPrepperConfig.PATTERNS_FILES_GLOB, patternsFilesGlob);
        settings.put(GrokPrepperConfig.TIMEOUT_MILLIS, timeoutMillis);
        settings.put(GrokPrepperConfig.TARGET_KEY, targetKey);

        return new PluginSetting(PLUGIN_NAME, settings);
    }

    private boolean equalRecords(final Record<String> first, final Record<String> second) throws JsonProcessingException {
        final Map<String, Object> recordMapFirst = OBJECT_MAPPER.readValue(first.getData(), MAP_TYPE_REFERENCE);
        final Map<String, Object> recordMapSecond = OBJECT_MAPPER.readValue(second.getData(), MAP_TYPE_REFERENCE);

        return recordMapFirst.equals(recordMapSecond);
    }
}