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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.detect.Detector;
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.exception.TikaConfigException;

public class ComponentNameResolverTest {

    /**
     * Resolving an unknown component name must produce an actionable message: it should
     * call out the two usual causes (a typo, or the providing module not being on the
     * classpath -- the TIKA-4750 scenario for tess4j-parser) rather than just stating
     * the registration rule.
     */
    @Test
    public void unregisteredComponentGivesActionableMessage() {
        ClassNotFoundException e = assertThrows(ClassNotFoundException.class,
                () -> ComponentNameResolver.resolveClass(
                        "definitely-not-a-real-parser-xyz", getClass().getClassLoader()));
        String msg = e.getMessage();
        assertTrue(msg.contains("definitely-not-a-real-parser-xyz"), msg);
        assertTrue(msg.contains("misspelled"), msg);
        assertTrue(msg.contains("not on the classpath"), msg);
        // names the opt-in-module cause concretely so users know what to add
        assertTrue(msg.contains("tika-parser-tess4j-module"), msg);
        assertTrue(msg.contains("Arbitrary class names are not allowed"), msg);
    }

    @Test
    public void registerRegistryRejectsNullIndexName() {
        ComponentRegistry registry = mock(ComponentRegistry.class);
        assertThrows(NullPointerException.class,
                () -> ComponentNameResolver.registerRegistry(null, registry));
    }

    @Test
    public void registerRegistryRejectsNullRegistry() {
        assertThrows(NullPointerException.class,
                () -> ComponentNameResolver.registerRegistry("null-registry-test", null));
    }

    @Test
    public void resolveClassFindsComponentInFirstRegistry()
            throws ClassNotFoundException, TikaConfigException {
        String componentName = "resolver-first-registry-test";
        ComponentRegistry firstRegistry = mock(ComponentRegistry.class);
        ComponentRegistry secondRegistry = mock(ComponentRegistry.class);
        ComponentNameResolver.registerRegistry("resolver-first-test", firstRegistry);
        ComponentNameResolver.registerRegistry("resolver-second-test", secondRegistry);
        when(firstRegistry.hasComponent(componentName)).thenReturn(true);
        doReturn(Detector.class).when(firstRegistry).getComponentClass(componentName);

        Class<?> result =
                ComponentNameResolver.resolveClass(componentName, getClass().getClassLoader());

        assertEquals(Detector.class, result);
        verify(firstRegistry).getComponentClass(componentName);
        verify(secondRegistry, never()).getComponentClass(componentName);
    }

    @Test
    public void resolveClassRejectsComponentMissingFromRegistries() {
        String componentName = "resolver-missing-component-test";
        ComponentRegistry firstRegistry = mock(ComponentRegistry.class);
        ComponentRegistry secondRegistry = mock(ComponentRegistry.class);
        ComponentNameResolver.registerRegistry("resolver-missing-first-test", firstRegistry);
        ComponentNameResolver.registerRegistry("resolver-missing-second-test", secondRegistry);
        when(firstRegistry.hasComponent(componentName)).thenReturn(false);
        when(secondRegistry.hasComponent(componentName)).thenReturn(false);

        assertThrows(ClassNotFoundException.class,
                () -> ComponentNameResolver.resolveClass(componentName, getClass().getClassLoader()));

        verify(firstRegistry).hasComponent(componentName);
        verify(secondRegistry).hasComponent(componentName);
    }

    @Test
    public void resolveClassRejectsComponentMissingAcrossMultipleRegistries() {
        String componentName = "resolver-missing-multiple-registries-test";
        ComponentRegistry firstRegistry = mock(ComponentRegistry.class);
        ComponentRegistry secondRegistry = mock(ComponentRegistry.class);
        ComponentNameResolver.registerRegistry("resolver-missing-multiple-first-test", firstRegistry);
        ComponentNameResolver.registerRegistry("resolver-missing-multiple-second-test", secondRegistry);
        when(firstRegistry.hasComponent(componentName)).thenReturn(false);
        when(secondRegistry.hasComponent(componentName)).thenReturn(false);

        assertThrows(ClassNotFoundException.class,
                () -> ComponentNameResolver.resolveClass(componentName, getClass().getClassLoader()));

        verify(firstRegistry).hasComponent(componentName);
        verify(secondRegistry).hasComponent(componentName);
    }

    @Test
    public void getFriendlyNameReturnsNullWhenNoRegistryHasName() {
        ComponentRegistry firstRegistry = mock(ComponentRegistry.class);
        ComponentRegistry secondRegistry = mock(ComponentRegistry.class);
        ComponentNameResolver.registerRegistry("friendly-name-first-test", firstRegistry);
        ComponentNameResolver.registerRegistry("friendly-name-second-test", secondRegistry);
        when(firstRegistry.getFriendlyName(DigesterFactory.class)).thenReturn(null);
        when(secondRegistry.getFriendlyName(DigesterFactory.class)).thenReturn(null);

        assertNull(ComponentNameResolver.getFriendlyName(DigesterFactory.class));
        verify(firstRegistry).getFriendlyName(DigesterFactory.class);
        verify(secondRegistry).getFriendlyName(DigesterFactory.class);
    }

    @Test
    public void hasComponentFindsComponentInRegisteredRegistry() {
        String componentName = "resolver-has-component-test";
        ComponentRegistry registry = mock(ComponentRegistry.class);
        when(registry.hasComponent(componentName)).thenReturn(true);
        ComponentNameResolver.registerRegistry("has-component-test", registry);

        assertTrue(ComponentNameResolver.hasComponent(componentName));
    }

    @Test
    public void hasComponentFindsComponentInAnyRegistry() {
        String componentName = "resolver-has-component-second-registry-test";
        ComponentRegistry firstRegistry = mock(ComponentRegistry.class);
        ComponentRegistry secondRegistry = mock(ComponentRegistry.class);
        when(firstRegistry.hasComponent(componentName)).thenReturn(false);
        when(secondRegistry.hasComponent(componentName)).thenReturn(true);
        ComponentNameResolver.registerRegistry("has-component-first-test", firstRegistry);
        ComponentNameResolver.registerRegistry("has-component-second-test", secondRegistry);

        assertTrue(ComponentNameResolver.hasComponent(componentName));
    }

    @Test
    public void getComponentInfoReturnsEmptyWhenComponentIsMissing() {
        String componentName = "resolver-component-info-missing-test";
        ComponentRegistry firstRegistry = mock(ComponentRegistry.class);
        ComponentRegistry secondRegistry = mock(ComponentRegistry.class);
        when(firstRegistry.hasComponent(componentName)).thenReturn(false);
        when(secondRegistry.hasComponent(componentName)).thenReturn(false);
        ComponentNameResolver.registerRegistry("component-info-first-test", firstRegistry);
        ComponentNameResolver.registerRegistry("component-info-second-test", secondRegistry);

        Optional<ComponentInfo> result = ComponentNameResolver.getComponentInfo(componentName);

        assertFalse(result.isPresent());
    }

    @Test
    public void getComponentInfoReturnsEmptyWhenRegistryThrows() throws TikaConfigException {
        String componentName = "resolver-component-info-exception-test";
        ComponentRegistry registry = mock(ComponentRegistry.class);
        when(registry.hasComponent(componentName)).thenReturn(true);
        when(registry.getComponentInfo(componentName))
                .thenThrow(new TikaConfigException("Simulated exception"));
        ComponentNameResolver.registerRegistry("component-info-exception-test", registry);

        Optional<ComponentInfo> result = ComponentNameResolver.getComponentInfo(componentName);

        assertFalse(result.isPresent());
    }
}
