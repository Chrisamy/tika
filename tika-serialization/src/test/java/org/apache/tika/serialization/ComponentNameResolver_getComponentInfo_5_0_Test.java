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

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaConfigException;

public class ComponentNameResolver_getComponentInfo_5_0_Test {

    private ComponentRegistry mockRegistry1;

    private ComponentRegistry mockRegistry2;

    @BeforeEach
    public void setUp() {
        mockRegistry1 = mock(ComponentRegistry.class);
        mockRegistry2 = mock(ComponentRegistry.class);
        ComponentNameResolver.registerRegistry("registry1", mockRegistry1);
        ComponentNameResolver.registerRegistry("registry2", mockRegistry2);
    }

    @Test
    public void testGetComponentInfo_RegistryDoesNotHaveComponent_ReturnsEmptyOptional() throws TikaConfigException {
        String componentName = "nonExistentComponent";
        when(mockRegistry1.hasComponent(componentName)).thenReturn(false);
        when(mockRegistry2.hasComponent(componentName)).thenReturn(false);
        Optional<ComponentInfo> result = ComponentNameResolver.getComponentInfo(componentName);
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetComponentInfo_ComponentInfoThrowsException_ReturnsEmptyOptional() throws TikaConfigException {
        String componentName = "exceptionComponent";
        Class<?> componentClass = Detector.class;
        ComponentInfo expectedComponentInfo = new ComponentInfo(componentClass, true);
        when(mockRegistry1.hasComponent(componentName)).thenReturn(true);
        when(mockRegistry1.getComponentInfo(componentName)).thenThrow(new TikaConfigException("Simulated exception"));
        Optional<ComponentInfo> result = ComponentNameResolver.getComponentInfo(componentName);
        assertFalse(result.isPresent());
    }
}
