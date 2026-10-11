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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.*;
import org.mockito.*;

import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaConfigException;

public class ComponentNameResolver_hasComponent_4_0_Test {

    private static final String INDEX_NAME = "testIndex";

    private static final String COMPONENT_NAME = "testComponent";

    private static final Class<?> COMPONENT_CLASS = Detector.class;

    @BeforeEach
    public void setUp() throws TikaConfigException {
        ComponentRegistry mockRegistry = mock(ComponentRegistry.class);
        when(mockRegistry.hasComponent(COMPONENT_NAME)).thenReturn(true);
        ComponentNameResolver.registerRegistry(INDEX_NAME, mockRegistry);
    }

    @Test
    public void testHasComponent_WhenComponentExists_ReturnsTrue() {
        assertTrue(ComponentNameResolver.hasComponent(COMPONENT_NAME));
    }

    @Test
    public void testHasComponent_WhenMultipleRegistries_ReturnsTrueForExistingComponent() throws TikaConfigException {
        ComponentRegistry mockRegistry1 = mock(ComponentRegistry.class);
        when(mockRegistry1.hasComponent(COMPONENT_NAME)).thenReturn(false);
        ComponentRegistry mockRegistry2 = mock(ComponentRegistry.class);
        when(mockRegistry2.hasComponent(COMPONENT_NAME)).thenReturn(true);
        ComponentNameResolver.registerRegistry("index1", mockRegistry1);
        ComponentNameResolver.registerRegistry("index2", mockRegistry2);
        assertTrue(ComponentNameResolver.hasComponent(COMPONENT_NAME));
    }
}
