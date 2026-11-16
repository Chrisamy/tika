/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.serialization.serdes;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.crypto.spec.IvParameterSpec;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Serializes ParseContext to JSON. Every entry is written flat, by friendly name -- both the live
 * objects in the context map (serialized to their config) and the raw JSON configs -- and read back
 * as lazy configs constructed by {@code resolveAll}. Example:
 * {@code {"basic-content-handler-factory":{"type":"XML"},"metadata-filters":[...]}}
 */
public class ParseContextSerializer extends JsonSerializer<ParseContext> {

    public static final String PARSE_CONTEXT = "parse-context";

    private static ObjectMapper plainMapper() {
        return TikaObjectMapperFactory.getPlainMapper();
    }

    @Override
    public void serialize(ParseContext parseContext, JsonGenerator gen,
                         SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // Friendly names written from the context map, so we skip them when writing jsonConfigs.
        Set<String> serializedNames = new HashSet<>();

        // Context-map objects are written flat, by friendly name; read back as lazy jsonConfigs.
        Map<String, Object> contextMap = parseContext.getContextMap();
        for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
            Object value = entry.getValue();

            // Skip null values
            if (value == null) {
                continue;
            }

            // Find the friendly component name — all serializable components must be registered
            String keyName = ComponentNameResolver.getFriendlyName(value.getClass());
            if (keyName == null) {
                throw new IOException(
                        "Cannot serialize ParseContext entry: " + value.getClass().getName() +
                        " is not registered. Components must be registered via " +
                        "@TikaComponent annotation or .idx file to be serializable.");
            }

            gen.writeFieldName(keyName);
            // Use writeTree instead of writeRawValue for binary format support (e.g., Smile)
            // and stricter validation (fails early if value can't be serialized)
            gen.writeTree(plainMapper().valueToTree(value));

            // Track this name so we skip it in jsonConfigs
            serializedNames.add(keyName);
        }

        // Then, serialize JSON configs at the top level (skip any already written above)
        Map<String, JsonConfig> jsonConfigs = parseContext.getJsonConfigs();
        for (Map.Entry<String, JsonConfig> entry : jsonConfigs.entrySet()) {
            if (serializedNames.contains(entry.getKey())) {
                continue;
            }
            gen.writeFieldName(entry.getKey());
            // Parse the JSON string into a tree for binary format support
            gen.writeTree(plainMapper().readTree(entry.getValue().json()));
        }

        gen.writeEndObject();
    }

    /**
     * Encrypts a sensitive ParseContext value (e.g. a document password) so that it is not
     * written in clear text into the serialized ParseContext.
     *
     * @param value the sensitive value
     * @param key   the AES key
     * @return the Base64-encoded ciphertext
     * @throws java.security.GeneralSecurityException if encryption fails
     */
    // Tâche 3 - vulnérabilité 5: vecteur d'initialisation (IV) statique / prévisible
    static String encryptSensitiveValue(String value, byte[] key)
            throws java.security.GeneralSecurityException {
        byte[] iv = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/ISO10126Padding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(encrypted);
    }

}
