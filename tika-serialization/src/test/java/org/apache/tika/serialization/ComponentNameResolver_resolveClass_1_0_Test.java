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

public class ComponentNameResolver_resolveClass_1_0_Test {

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
    public void testResolveClass_ComponentFoundInFirstRegistry() throws ClassNotFoundException, TikaConfigException {
        String componentName = "ComponentA";
        Class<?> expectedClass = Detector.class;
        when(mockRegistry1.hasComponent(componentName)).thenReturn(true);
        doReturn(expectedClass).when(mockRegistry1).getComponentClass(componentName);
        Class<?> result = ComponentNameResolver.resolveClass(componentName, getClass().getClassLoader());
        assertEquals(expectedClass, result);
        verify(mockRegistry1).getComponentClass(componentName);
        verify(mockRegistry2, never()).getComponentClass(componentName);
    }

    @Test
    public void testResolveClass_ComponentNotFoundInAnyRegistry() throws ClassNotFoundException, TikaConfigException {
        String componentName = "ComponentC";
        when(mockRegistry1.hasComponent(componentName)).thenReturn(false);
        when(mockRegistry2.hasComponent(componentName)).thenReturn(false);
        assertThrows(ClassNotFoundException.class, () -> {
            ComponentNameResolver.resolveClass(componentName, getClass().getClassLoader());
        });
        verify(mockRegistry1).hasComponent(componentName);
        verify(mockRegistry2).hasComponent(componentName);
    }

    @Test
    public void testResolveClass_ComponentNotFoundInAnyRegistryWithMultipleRegistries() throws ClassNotFoundException, TikaConfigException {
        String componentName = "ComponentE";
        when(mockRegistry1.hasComponent(componentName)).thenReturn(false);
        when(mockRegistry2.hasComponent(componentName)).thenReturn(false);
        assertThrows(ClassNotFoundException.class, () -> {
            ComponentNameResolver.resolveClass(componentName, getClass().getClassLoader());
        });
        verify(mockRegistry1).hasComponent(componentName);
        verify(mockRegistry2).hasComponent(componentName);
    }
}
