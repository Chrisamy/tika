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
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.exception.TikaConfigException;

public class ComponentNameResolver_getFriendlyName_3_0_Test {

    private ComponentRegistry mockRegistry1;

    private ComponentRegistry mockRegistry2;

    @BeforeEach
    public void setUp() throws TikaConfigException {
        mockRegistry1 = mock(ComponentRegistry.class);
        mockRegistry2 = mock(ComponentRegistry.class);
        ComponentNameResolver.registerRegistry("registry1", mockRegistry1);
        ComponentNameResolver.registerRegistry("registry2", mockRegistry2);
    }

    @Test
    public void testGetFriendlyName_NoFriendlyNameInAnyRegistry() {
        Class<?> testClass = DigesterFactory.class;
        when(mockRegistry1.getFriendlyName(testClass)).thenReturn(null);
        when(mockRegistry2.getFriendlyName(testClass)).thenReturn(null);
        String result = ComponentNameResolver.getFriendlyName(testClass);
        assertNull(result);
        verify(mockRegistry1).getFriendlyName(testClass);
        verify(mockRegistry2).getFriendlyName(testClass);
    }
}
