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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.UnpackSelector;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.Parser;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.sax.ContentHandlerDecoratorFactory;
import org.apache.tika.sax.ContentHandlerFactory;

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

    @Test
    public void determineContextKeyPrefersExplicitKey() {
        ComponentInfo info = new ComponentInfo(Object.class, true, Parser.class);

        assertEquals(Parser.class, ComponentNameResolver.determineContextKey(info));
    }

    @Test
    public void determineContextKeyUsesImplementedContextInterface() {
        ComponentInfo info = new ComponentInfo(Detector.class, true);

        assertEquals(Detector.class, ComponentNameResolver.determineContextKey(info));
    }

    @Test
    public void determineContextKeyFallsBackToComponentClass() {
        ComponentInfo info = new ComponentInfo(Object.class, true);

        assertEquals(Object.class, ComponentNameResolver.determineContextKey(info));
    }

    @Test
    public void getContextKeyInterfacesReturnsAllSupportedInterfaces() {
        Set<Class<?>> expected = Set.of(Parser.class, Detector.class, EncodingDetector.class,
                MetadataFilter.class, Translator.class, Renderer.class, DigesterFactory.class,
                EmbeddedDocumentExtractorFactory.class, MetadataWriteLimiterFactory.class,
                ContentHandlerDecoratorFactory.class, ContentHandlerFactory.class,
                UnpackSelector.class);

        assertEquals(expected, ComponentNameResolver.getContextKeyInterfaces());
        assertThrows(UnsupportedOperationException.class,
                () -> ComponentNameResolver.getContextKeyInterfaces().add(String.class));
    }

    @Test
    public void findContextKeyInterfaceFindsSupportedInterfaces() {
        Set<Class<?>> interfaces = ComponentNameResolver.getContextKeyInterfaces();

        for (Class<?> contextKeyInterface : interfaces) {
            assertEquals(contextKeyInterface,
                    ComponentNameResolver.findContextKeyInterface(contextKeyInterface));
        }
        assertNull(ComponentNameResolver.findContextKeyInterface(String.class));
    }

    @Test
    public void getWireInstantiableContextKeysReturnsAllowlist() {
        Set<Class<?>> expected = Set.of(MetadataFilter.class, ContentHandlerFactory.class,
                ContentHandlerDecoratorFactory.class, DigesterFactory.class,
                MetadataWriteLimiterFactory.class, UnpackSelector.class);

        assertEquals(expected, ComponentNameResolver.getWireInstantiableContextKeys());
        assertThrows(UnsupportedOperationException.class,
                () -> ComponentNameResolver.getWireInstantiableContextKeys().add(String.class));
    }

    @Test
    public void getWireBlockedContextKeysReturnsBlocklist() {
        Set<Class<?>> expected = Set.of(Parser.class, Detector.class, EncodingDetector.class,
                Renderer.class, Translator.class, EmbeddedDocumentExtractorFactory.class);

        assertEquals(expected, ComponentNameResolver.getWireBlockedContextKeys());
        assertThrows(UnsupportedOperationException.class,
                () -> ComponentNameResolver.getWireBlockedContextKeys().add(String.class));
    }

    @Test
    public void contextKeyInterfacesAreClassifiedAsAllowedOrBlocked() {
        Set<Class<?>> classified = new HashSet<>(
                ComponentNameResolver.getWireInstantiableContextKeys());
        classified.addAll(ComponentNameResolver.getWireBlockedContextKeys());

        assertEquals(ComponentNameResolver.getContextKeyInterfaces(), classified);
        assertTrue(Collections.disjoint(
                ComponentNameResolver.getWireInstantiableContextKeys(),
                ComponentNameResolver.getWireBlockedContextKeys()));
    }

    @Test
    public void isWireInstantiableAllowsOnlyAllowlistedInterfaces() {
        assertTrue(ComponentNameResolver.isWireInstantiable(MetadataFilter.class));
        assertTrue(ComponentNameResolver.isWireInstantiable(ContentHandlerFactory.class));
        assertTrue(ComponentNameResolver.isWireInstantiable(ContentHandlerDecoratorFactory.class));
        assertTrue(ComponentNameResolver.isWireInstantiable(DigesterFactory.class));
        assertTrue(ComponentNameResolver.isWireInstantiable(MetadataWriteLimiterFactory.class));
        assertTrue(ComponentNameResolver.isWireInstantiable(UnpackSelector.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(Parser.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(Detector.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(EncodingDetector.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(Translator.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(Renderer.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(EmbeddedDocumentExtractorFactory.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(String.class));
    }

    @Test
    public void isWireBlockedBlocksNonAllowlistedContextInterfaces() {
        assertTrue(ComponentNameResolver.isWireBlocked(Parser.class));
        assertTrue(ComponentNameResolver.isWireBlocked(Detector.class));
        assertTrue(ComponentNameResolver.isWireBlocked(EncodingDetector.class));
        assertTrue(ComponentNameResolver.isWireBlocked(Renderer.class));
        assertTrue(ComponentNameResolver.isWireBlocked(Translator.class));
        assertTrue(ComponentNameResolver.isWireBlocked(EmbeddedDocumentExtractorFactory.class));
        assertFalse(ComponentNameResolver.isWireBlocked(MetadataFilter.class));
        assertFalse(ComponentNameResolver.isWireBlocked(ContentHandlerFactory.class));
        assertFalse(ComponentNameResolver.isWireBlocked(ContentHandlerDecoratorFactory.class));
        assertFalse(ComponentNameResolver.isWireBlocked(DigesterFactory.class));
        assertFalse(ComponentNameResolver.isWireBlocked(MetadataWriteLimiterFactory.class));
        assertFalse(ComponentNameResolver.isWireBlocked(UnpackSelector.class));
        assertFalse(ComponentNameResolver.isWireBlocked(String.class));
    }

    @Test
    public void usesCompactFormatForContextInterfaces() {
        for (Class<?> contextKeyInterface : ComponentNameResolver.getContextKeyInterfaces()) {
            assertTrue(ComponentNameResolver.usesCompactFormat(contextKeyInterface));
        }
        assertFalse(ComponentNameResolver.usesCompactFormat(String.class));
    }

    @Test
    public void hasImplementationsOfFindsAssignableComponents() {
        ComponentRegistry registry = mock(ComponentRegistry.class);
        HashMap<String, ComponentInfo> components = new HashMap<>();
        components.put("resolver-parser-implementation",
                new ComponentInfo(Parser.class, true));
        components.put("resolver-detector-implementation",
                new ComponentInfo(Detector.class, false));
        when(registry.getAllComponents()).thenReturn(components);
        ComponentNameResolver.registerRegistry("resolver-implementations-test", registry);

        assertTrue(ComponentNameResolver.hasImplementationsOf(Parser.class));
        assertTrue(ComponentNameResolver.hasImplementationsOf(Object.class));
        assertFalse(ComponentNameResolver.hasImplementationsOf(String.class));
    }

    @Test
    public void registerComponentConfigRejectsNullConfig() {
        assertThrows(NullPointerException.class,
                () -> ComponentNameResolver.registerComponentConfig(null));
    }

    @Test
    public void registerComponentConfigRejectsConfigWithoutComponentClass() {
        ComponentConfig<?> config = mock(ComponentConfig.class);
        when(config.getComponentClass()).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> ComponentNameResolver.registerComponentConfig(config));
    }

    @Test
    public void registerComponentConfigMakesConfigAvailableByComponentClass() {
        ComponentConfig<Detector> config =
                ComponentConfig.builder("resolver-detectors-test", Detector.class).build();

        ComponentNameResolver.registerComponentConfig(config);

        ComponentConfig<Detector> result = ComponentNameResolver.getComponentConfig(Detector.class);
        assertNotNull(result);
        assertSame(config, result);
    }

    @Test
    public void getContextKeyReturnsRegisteredExplicitKey() throws TikaConfigException {
        Class<?> componentClass = FirstContextKeyComponent.class;
        String friendlyName = "resolver-context-key-first-test";
        ComponentRegistry registry = mock(ComponentRegistry.class);
        when(registry.getFriendlyName(componentClass)).thenReturn(friendlyName);
        when(registry.getComponentInfo(friendlyName))
                .thenReturn(new ComponentInfo(componentClass, true, Parser.class));
        ComponentNameResolver.registerRegistry("resolver-context-key-first-test", registry);

        assertEquals(Parser.class, ComponentNameResolver.getContextKey(componentClass));
    }

    @Test
    public void getContextKeyReturnsNullWhenNoRegistryHasFriendlyName() {
        Class<?> componentClass = MissingContextKeyComponent.class;
        ComponentRegistry registry = mock(ComponentRegistry.class);
        when(registry.getFriendlyName(componentClass)).thenReturn(null);
        ComponentNameResolver.registerRegistry("resolver-context-key-missing-test", registry);

        assertNull(ComponentNameResolver.getContextKey(componentClass));
    }

    @Test
    public void getContextKeyReturnsNullWhenRegistryCannotLoadComponentInfo()
            throws TikaConfigException {
        Class<?> componentClass = FailedContextKeyComponent.class;
        String friendlyName = "resolver-context-key-failure-test";
        ComponentRegistry registry = mock(ComponentRegistry.class);
        when(registry.getFriendlyName(componentClass)).thenReturn(friendlyName);
        when(registry.getComponentInfo(friendlyName))
                .thenThrow(new TikaConfigException("Simulated exception"));
        ComponentNameResolver.registerRegistry("resolver-context-key-failure-test", registry);

        assertNull(ComponentNameResolver.getContextKey(componentClass));
    }

    private static class FirstContextKeyComponent {
    }

    private static class MissingContextKeyComponent {
    }

    private static class FailedContextKeyComponent {
    }
}
