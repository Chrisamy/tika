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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.*;

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

    @Nested
    class ComponentNameResolver_registerRegistry_0_0_Test {

        @Test
        public void testRegisterRegistryWithNullIndexName() {
            // Arrange
            String indexName = null;
            ComponentRegistry registry = mock(ComponentRegistry.class);
            // Act & Assert
            assertThrows(NullPointerException.class, () -> {
                ComponentNameResolver.registerRegistry(indexName, registry);
            });
        }

        @Test
        public void testRegisterRegistryWithNullRegistry() {
            // Arrange
            String indexName = "testIndex";
            ComponentRegistry registry = null;
            // Act & Assert
            assertThrows(NullPointerException.class, () -> {
                ComponentNameResolver.registerRegistry(indexName, registry);
            });
        }
    }

    @Nested
    class ComponentNameResolver_resolveClass_1_0_Test {

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

    @Nested
    class ComponentNameResolver_getFriendlyName_3_0_Test {

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

    @Nested
    class ComponentNameResolver_hasComponent_4_0_Test {

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

    @Nested
    class ComponentNameResolver_getComponentInfo_5_0_Test {

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

    @Nested
    class ComponentNameResolver_hasImplementationsOf_6_0_Test {

        private static final String INDEX_NAME = "testIndex";

        private static final ClassLoader CLASS_LOADER = ClassLoader.getSystemClassLoader();

        @BeforeEach
        public void setUp() throws TikaConfigException {
            ComponentRegistry registry = mock(ComponentRegistry.class);
            Map<String, ComponentInfo> components = new HashMap<>();
            components.put("component1", new ComponentInfo(Parser.class, true));
            components.put("component2", new ComponentInfo(Detector.class, false));
            when(registry.getAllComponents()).thenReturn(components);
            ComponentNameResolver.registerRegistry(INDEX_NAME, registry);
        }

        @Test
        public void testHasImplementationsOf_withMatchingComponent() {
            assertTrue(ComponentNameResolver.hasImplementationsOf(Parser.class));
        }

        @Test
        public void testHasImplementationsOf_withAbstractType() {
            assertTrue(ComponentNameResolver.hasImplementationsOf(Object.class));
        }
    }

    @Nested
    class ComponentNameResolver_registerComponentConfig_7_0_Test {

        @Test
        public void testRegisterComponentConfigWithNullConfig() {
            // Arrange
            ComponentConfig<?> config = null;
            // Act & Assert
            assertThrows(NullPointerException.class, () -> {
                ComponentNameResolver.registerComponentConfig(config);
            });
        }

        @Test
        public void testRegisterComponentConfigWithNullComponentClass() {
            // Arrange
            ComponentConfig<?> config = mock(ComponentConfig.class);
            when(config.getComponentClass()).thenReturn(null);
            // Act & Assert
            assertThrows(NullPointerException.class, () -> {
                ComponentNameResolver.registerComponentConfig(config);
            });
        }
    }

    @Nested
    class ComponentNameResolver_getContextKeyInterfaces_9_0_Test {

        @Test
        public void testGetContextKeyInterfaces() {
            // Given
            Set<Class<?>> expectedInterfaces = Collections.unmodifiableSet(Set.of(Parser.class, Detector.class, EncodingDetector.class, MetadataFilter.class, Translator.class, Renderer.class, DigesterFactory.class, EmbeddedDocumentExtractorFactory.class, MetadataWriteLimiterFactory.class, ContentHandlerDecoratorFactory.class, ContentHandlerFactory.class, UnpackSelector.class));
            // When
            Set<Class<?>> actualInterfaces = ComponentNameResolver.getContextKeyInterfaces();
            // Then
            assertEquals(expectedInterfaces, actualInterfaces);
        }
    }

    @Nested
    class ComponentNameResolver_getWireInstantiableContextKeys_10_0_Test {

        @Test
        public void testGetWireInstantiableContextKeys() {
            // Given
            Set<Class<?>> expectedWireInstantiableContextKeys = Set.of(MetadataFilter.class, ContentHandlerFactory.class, ContentHandlerDecoratorFactory.class, DigesterFactory.class, MetadataWriteLimiterFactory.class, UnpackSelector.class);
            // When
            Set<Class<?>> actualWireInstantiableContextKeys = ComponentNameResolver.getWireInstantiableContextKeys();
            // Then
            assertEquals(expectedWireInstantiableContextKeys, actualWireInstantiableContextKeys);
        }
    }

    @Nested
    class ComponentNameResolver_isWireInstantiable_11_0_Test {

        @Test
        public void testIsWireInstantiable_WithMetadataFilter() {
            assertTrue(ComponentNameResolver.isWireInstantiable(MetadataFilter.class));
        }

        @Test
        public void testIsWireInstantiable_WithContentHandlerFactory() {
            assertTrue(ComponentNameResolver.isWireInstantiable(ContentHandlerFactory.class));
        }

        @Test
        public void testIsWireInstantiable_WithContentHandlerDecoratorFactory() {
            assertTrue(ComponentNameResolver.isWireInstantiable(ContentHandlerDecoratorFactory.class));
        }

        @Test
        public void testIsWireInstantiable_WithDigesterFactory() {
            assertTrue(ComponentNameResolver.isWireInstantiable(DigesterFactory.class));
        }

        @Test
        public void testIsWireInstantiable_WithMetadataWriteLimiterFactory() {
            assertTrue(ComponentNameResolver.isWireInstantiable(MetadataWriteLimiterFactory.class));
        }

        @Test
        public void testIsWireInstantiable_WithUnpackSelector() {
            assertTrue(ComponentNameResolver.isWireInstantiable(UnpackSelector.class));
        }

        @Test
        public void testIsWireInstantiable_WithParser() {
            assertFalse(ComponentNameResolver.isWireInstantiable(Parser.class));
        }

        @Test
        public void testIsWireInstantiable_WithDetector() {
            assertFalse(ComponentNameResolver.isWireInstantiable(Detector.class));
        }

        @Test
        public void testIsWireInstantiable_WithEncodingDetector() {
            assertFalse(ComponentNameResolver.isWireInstantiable(EncodingDetector.class));
        }

        @Test
        public void testIsWireInstantiable_WithTranslator() {
            assertFalse(ComponentNameResolver.isWireInstantiable(Translator.class));
        }

        @Test
        public void testIsWireInstantiable_WithRenderer() {
            assertFalse(ComponentNameResolver.isWireInstantiable(Renderer.class));
        }

        @Test
        public void testIsWireInstantiable_WithEmbeddedDocumentExtractorFactory() {
            assertFalse(ComponentNameResolver.isWireInstantiable(EmbeddedDocumentExtractorFactory.class));
        }
    }

    @Nested
    class ComponentNameResolver_isWireBlocked_12_0_Test {

        @Test
        public void testIsWireBlocked_ParserBlocked() {
            assertTrue(ComponentNameResolver.isWireBlocked(Parser.class));
        }

        @Test
        public void testIsWireBlocked_DetectorBlocked() {
            assertTrue(ComponentNameResolver.isWireBlocked(Detector.class));
        }

        @Test
        public void testIsWireBlocked_EncodingDetectorBlocked() {
            assertTrue(ComponentNameResolver.isWireBlocked(EncodingDetector.class));
        }

        @Test
        public void testIsWireBlocked_RendererBlocked() {
            assertTrue(ComponentNameResolver.isWireBlocked(Renderer.class));
        }

        @Test
        public void testIsWireBlocked_TranslatorBlocked() {
            assertTrue(ComponentNameResolver.isWireBlocked(Translator.class));
        }

        @Test
        public void testIsWireBlocked_EmbeddedDocumentExtractorFactoryBlocked() {
            assertTrue(ComponentNameResolver.isWireBlocked(EmbeddedDocumentExtractorFactory.class));
        }

        @Test
        public void testIsWireBlocked_MetadataFilterNotBlocked() {
            assertFalse(ComponentNameResolver.isWireBlocked(MetadataFilter.class));
        }

        @Test
        public void testIsWireBlocked_ContentHandlerFactoryNotBlocked() {
            assertFalse(ComponentNameResolver.isWireBlocked(ContentHandlerFactory.class));
        }

        @Test
        public void testIsWireBlocked_ContentHandlerDecoratorFactoryNotBlocked() {
            assertFalse(ComponentNameResolver.isWireBlocked(ContentHandlerDecoratorFactory.class));
        }

        @Test
        public void testIsWireBlocked_DigesterFactoryNotBlocked() {
            assertFalse(ComponentNameResolver.isWireBlocked(DigesterFactory.class));
        }

        @Test
        public void testIsWireBlocked_MetadataWriteLimiterFactoryNotBlocked() {
            assertFalse(ComponentNameResolver.isWireBlocked(MetadataWriteLimiterFactory.class));
        }

        @Test
        public void testIsWireBlocked_UnpackSelectorNotBlocked() {
            assertFalse(ComponentNameResolver.isWireBlocked(UnpackSelector.class));
        }

        @Test
        public void testIsWireBlocked_CustomClassNotBlocked() {
            assertFalse(ComponentNameResolver.isWireBlocked(String.class));
        }
    }

    @Nested
    class ComponentNameResolver_getWireBlockedContextKeys_13_0_Test {

        @Test
        public void testGetWireBlockedContextKeys() {
            // Given
            Set<Class<?>> expectedWireBlockedContextKeys = Set.of(Parser.class, Detector.class, EncodingDetector.class, Renderer.class, Translator.class, EmbeddedDocumentExtractorFactory.class);
            // When
            Set<Class<?>> actualWireBlockedContextKeys = ComponentNameResolver.getWireBlockedContextKeys();
            // Then
            assertEquals(expectedWireBlockedContextKeys, actualWireBlockedContextKeys);
        }
    }

    @Nested
    class ComponentNameResolver_findContextKeyInterface_14_0_Test {

        @Test
        public void testFindContextKeyInterface_withParser() {
            // Given
            Class<?> type = Parser.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(Parser.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withDetector() {
            // Given
            Class<?> type = Detector.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(Detector.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withEncodingDetector() {
            // Given
            Class<?> type = EncodingDetector.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(EncodingDetector.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withMetadataFilter() {
            // Given
            Class<?> type = MetadataFilter.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(MetadataFilter.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withTranslator() {
            // Given
            Class<?> type = Translator.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(Translator.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withRenderer() {
            // Given
            Class<?> type = Renderer.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(Renderer.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withDigesterFactory() {
            // Given
            Class<?> type = DigesterFactory.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(DigesterFactory.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withEmbeddedDocumentExtractorFactory() {
            // Given
            Class<?> type = EmbeddedDocumentExtractorFactory.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(EmbeddedDocumentExtractorFactory.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withMetadataWriteLimiterFactory() {
            // Given
            Class<?> type = MetadataWriteLimiterFactory.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(MetadataWriteLimiterFactory.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withContentHandlerDecoratorFactory() {
            // Given
            Class<?> type = ContentHandlerDecoratorFactory.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(ContentHandlerDecoratorFactory.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withContentHandlerFactory() {
            // Given
            Class<?> type = ContentHandlerFactory.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(ContentHandlerFactory.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withUnpackSelector() {
            // Given
            Class<?> type = UnpackSelector.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertEquals(UnpackSelector.class, result);
        }

        @Test
        public void testFindContextKeyInterface_withNonContextKeyInterface() {
            // Given
            Class<?> type = String.class;
            // When
            Class<?> result = ComponentNameResolver.findContextKeyInterface(type);
            // Then
            assertNull(result);
        }
    }

    @Nested
    class ComponentNameResolver_usesCompactFormat_15_0_Test {

        @Test
        public void testUsesCompactFormat_WithParser() {
            assertTrue(ComponentNameResolver.usesCompactFormat(Parser.class));
        }

        @Test
        public void testUsesCompactFormat_WithDetector() {
            assertTrue(ComponentNameResolver.usesCompactFormat(Detector.class));
        }

        @Test
        public void testUsesCompactFormat_WithEncodingDetector() {
            assertTrue(ComponentNameResolver.usesCompactFormat(EncodingDetector.class));
        }

        @Test
        public void testUsesCompactFormat_WithMetadataFilter() {
            assertTrue(ComponentNameResolver.usesCompactFormat(MetadataFilter.class));
        }

        @Test
        public void testUsesCompactFormat_WithTranslator() {
            assertTrue(ComponentNameResolver.usesCompactFormat(Translator.class));
        }

        @Test
        public void testUsesCompactFormat_WithRenderer() {
            assertTrue(ComponentNameResolver.usesCompactFormat(Renderer.class));
        }

        @Test
        public void testUsesCompactFormat_WithDigesterFactory() {
            assertTrue(ComponentNameResolver.usesCompactFormat(DigesterFactory.class));
        }

        @Test
        public void testUsesCompactFormat_WithEmbeddedDocumentExtractorFactory() {
            assertTrue(ComponentNameResolver.usesCompactFormat(EmbeddedDocumentExtractorFactory.class));
        }

        @Test
        public void testUsesCompactFormat_WithMetadataWriteLimiterFactory() {
            assertTrue(ComponentNameResolver.usesCompactFormat(MetadataWriteLimiterFactory.class));
        }

        @Test
        public void testUsesCompactFormat_WithContentHandlerDecoratorFactory() {
            assertTrue(ComponentNameResolver.usesCompactFormat(ContentHandlerDecoratorFactory.class));
        }

        @Test
        public void testUsesCompactFormat_WithContentHandlerFactory() {
            assertTrue(ComponentNameResolver.usesCompactFormat(ContentHandlerFactory.class));
        }

        @Test
        public void testUsesCompactFormat_WithUnpackSelector() {
            assertTrue(ComponentNameResolver.usesCompactFormat(UnpackSelector.class));
        }

        @Test
        public void testUsesCompactFormat_WithNonContextKeyInterface() {
            assertFalse(ComponentNameResolver.usesCompactFormat(String.class));
        }
    }

    @Nested
    class ComponentNameResolver_determineContextKey_16_0_Test {

        @Test
        public void testDetermineContextKey_withContextKeySet() {
            // Arrange
            Class<?> expectedContextKey = Parser.class;
            ComponentInfo info = new ComponentInfo(Object.class, true, expectedContextKey);
            // Act
            Class<?> result = ComponentNameResolver.determineContextKey(info);
            // Assert
            assertEquals(expectedContextKey, result);
        }

        @Test
        public void testDetermineContextKey_withInterfaceKeyFound() {
            // Arrange
            Class<?> componentClass = Detector.class;
            Class<?> expectedContextKey = Detector.class;
            ComponentInfo info = new ComponentInfo(componentClass, true);
            // Act
            Class<?> result = ComponentNameResolver.determineContextKey(info);
            // Assert
            assertEquals(expectedContextKey, result);
        }

        @Test
        public void testDetermineContextKey_withNoContextKeyOrInterfaceKey() {
            // Arrange
            Class<?> componentClass = Object.class;
            ComponentInfo info = new ComponentInfo(componentClass, true);
            // Act
            Class<?> result = ComponentNameResolver.determineContextKey(info);
            // Assert
            assertEquals(componentClass, result);
        }

        @Test
        public void testDetermineContextKey_withNullContextKeyAndNoInterfaceKey() {
            // Arrange
            Class<?> componentClass = Object.class;
            ComponentInfo info = new ComponentInfo(componentClass, true, null);
            // Act
            Class<?> result = ComponentNameResolver.determineContextKey(info);
            // Assert
            assertEquals(componentClass, result);
        }
    }

    @Nested
    class ComponentNameResolver_getContextKey_17_0_Test {

        private ComponentRegistry mockRegistry1;

        private ComponentRegistry mockRegistry2;

        @BeforeEach
        public void setUp() {
            mockRegistry1 = mock(ComponentRegistry.class);
            mockRegistry2 = mock(ComponentRegistry.class);
            // Register mock registries
            ComponentNameResolver.registerRegistry("registry1", mockRegistry1);
            ComponentNameResolver.registerRegistry("registry2", mockRegistry2);
        }

        @Test
        public void testGetContextKey_FriendlyNameFoundInFirstRegistry() throws TikaConfigException {
            // Arrange
            Class<?> testClass = Parser.class;
            String friendlyName = "parserFriendlyName";
            ComponentInfo mockComponentInfo = new ComponentInfo(testClass, true, testClass);
            when(mockRegistry1.getFriendlyName(testClass)).thenReturn(friendlyName);
            when(mockRegistry1.getComponentInfo(friendlyName)).thenReturn(mockComponentInfo);
            // Act
            Class<?> result = ComponentNameResolver.getContextKey(testClass);
            // Assert
            assertEquals(testClass, result);
        }

        @Test
        public void testGetContextKey_FriendlyNameFoundInSecondRegistry() throws TikaConfigException {
            // Arrange
            Class<?> testClass = Detector.class;
            String friendlyName = "detectorFriendlyName";
            ComponentInfo mockComponentInfo = new ComponentInfo(testClass, true, testClass);
            when(mockRegistry1.getFriendlyName(testClass)).thenReturn(null);
            when(mockRegistry2.getFriendlyName(testClass)).thenReturn(friendlyName);
            when(mockRegistry2.getComponentInfo(friendlyName)).thenReturn(mockComponentInfo);
            // Act
            Class<?> result = ComponentNameResolver.getContextKey(testClass);
            // Assert
            assertEquals(testClass, result);
        }

        @Test
        public void testGetContextKey_FriendlyNameNotFoundInAnyRegistry() throws TikaConfigException {
            // Arrange
            Class<?> testClass = EncodingDetector.class;
            when(mockRegistry1.getFriendlyName(testClass)).thenReturn(null);
            when(mockRegistry2.getFriendlyName(testClass)).thenReturn(null);
            // Act
            Class<?> result = ComponentNameResolver.getContextKey(testClass);
            // Assert
            assertNull(result);
        }

        @Test
        public void testGetContextKey_ComponentInfoThrowsExceptionInFirstRegistry() throws TikaConfigException {
            // Arrange
            Class<?> testClass = MetadataFilter.class;
            String friendlyName = "metadataFilterFriendlyName";
            when(mockRegistry1.getFriendlyName(testClass)).thenReturn(friendlyName);
            when(mockRegistry1.getComponentInfo(friendlyName)).thenThrow(new TikaConfigException("Simulated exception"));
            // Act
            Class<?> result = ComponentNameResolver.getContextKey(testClass);
            // Assert
            assertNull(result);
        }

        @Test
        public void testGetContextKey_ComponentInfoThrowsExceptionInSecondRegistry() throws TikaConfigException {
            // Arrange
            Class<?> testClass = Renderer.class;
            String friendlyName = "rendererFriendlyName";
            when(mockRegistry1.getFriendlyName(testClass)).thenReturn(null);
            when(mockRegistry2.getFriendlyName(testClass)).thenReturn(friendlyName);
            when(mockRegistry2.getComponentInfo(friendlyName)).thenThrow(new TikaConfigException("Simulated exception"));
            // Act
            Class<?> result = ComponentNameResolver.getContextKey(testClass);
            // Assert
            assertNull(result);
        }
    }
}
