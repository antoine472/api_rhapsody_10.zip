// modificationCounter = 2
//
// changelog:
// - 2026-07-17 - Implemented secure XML loading and checker lookup near the plugin JAR.
// - 2026-07-27 - Made XML parser selection deterministic and tolerated unsupported optional JAXP access restrictions.

package com.safran.checker.config;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Loads and indexes the external Safran checker configuration.
 */
public final class CheckerConfigurationRepository {

    public static final String DEFAULT_CONFIGURATION_FILE_NAME =
            "safran-checkers.xml";

    private static final String FEATURE_DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";

    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";

    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";

    private static final String FEATURE_LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final String DIAGNOSTIC_PREFIX =
            "[SafranChecker XML configuration] ";

    private final Path configurationPath;
    private final List<CheckerDefinition> definitions;
    private final Map<String, CheckerDefinition> definitionsByClassName;

    /**
     * Creates an immutable checker configuration repository.
     *
     * @param configurationPath source XML configuration path
     * @param definitions parsed checker definitions
     */
    private CheckerConfigurationRepository(
            Path configurationPath,
            List<CheckerDefinition> definitions) {

        this.configurationPath =
                configurationPath;

        this.definitions =
                List.copyOf(definitions);

        Map<String, CheckerDefinition> indexedDefinitions =
                new LinkedHashMap<>();

        for (CheckerDefinition definition : definitions) {
            CheckerDefinition previousDefinition =
                    indexedDefinitions.put(
                            definition.getJavaClassName(),
                            definition);

            if (previousDefinition != null) {
                throw new IllegalArgumentException(
                        "Duplicate checker javaClassName: "
                                + definition.getJavaClassName());
            }
        }

        this.definitionsByClassName =
                Collections.unmodifiableMap(indexedDefinitions);
    }

    /**
     * Loads the checker configuration located next to a plugin class.
     *
     * @param anchorClass class used to locate the plugin installation directory
     * @return loaded checker configuration repository
     */
    public static CheckerConfigurationRepository loadNextToClass(
            Class<?> anchorClass) {

        Path configurationPath =
                locateNextToClass(
                        anchorClass,
                        DEFAULT_CONFIGURATION_FILE_NAME);

        return load(configurationPath);
    }

    /**
     * Loads a checker configuration XML file.
     *
     * @param configurationPath XML configuration path
     * @return loaded checker configuration repository
     */
    public static CheckerConfigurationRepository load(
            Path configurationPath) {

        if (configurationPath == null
                || !Files.isRegularFile(configurationPath)) {

            throw new IllegalStateException(
                    "Checker configuration file not found: "
                            + configurationPath);
        }

        try (InputStream inputStream =
                     Files.newInputStream(configurationPath)) {

            DocumentBuilderFactory factory =
                    createSecureDocumentBuilderFactory();

            Document document =
                    factory
                            .newDocumentBuilder()
                            .parse(inputStream);

            return new CheckerConfigurationRepository(
                    configurationPath
                            .toAbsolutePath()
                            .normalize(),
                    parseDefinitions(document));

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot load checker configuration from "
                            + configurationPath,
                    exception);
        }
    }

    /**
     * Creates a deterministic and hardened XML parser factory.
     *
     * <p>{@link DocumentBuilderFactory#newDefaultInstance()} deliberately
     * bypasses third-party JAXP providers discovered through the Rhapsody
     * plugin classpath. This prevents startup behaviour from depending on
     * plugin and library loading order.</p>
     *
     * @return configured XML parser factory
     * @throws ParserConfigurationException when a required security feature
     *         cannot be configured
     */
    private static DocumentBuilderFactory
            createSecureDocumentBuilderFactory()
            throws ParserConfigurationException {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newDefaultInstance();

        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        /*
         * These features are required. If the JDK default parser cannot apply
         * one of them, configuration loading must fail with an explicit cause.
         */
        factory.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING,
                true);

        factory.setFeature(
                FEATURE_DISALLOW_DOCTYPE,
                true);

        factory.setFeature(
                FEATURE_EXTERNAL_GENERAL_ENTITIES,
                false);

        factory.setFeature(
                FEATURE_EXTERNAL_PARAMETER_ENTITIES,
                false);

        factory.setFeature(
                FEATURE_LOAD_EXTERNAL_DTD,
                false);

        /*
         * These JAXP access restrictions are complementary protections.
         * Some Xerces versions embedded in host applications do not recognize
         * them. The mandatory features above already block DTD and external
         * entity processing, so an unsupported optional attribute must not
         * prevent the checker plugin from starting.
         */
        setOptionalFactoryAttribute(
                factory,
                XMLConstants.ACCESS_EXTERNAL_DTD,
                "");

        setOptionalFactoryAttribute(
                factory,
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                "");

        return factory;
    }

    /**
     * Applies an optional JAXP factory attribute when the active provider
     * supports it.
     *
     * @param factory XML parser factory
     * @param attributeName JAXP attribute name
     * @param attributeValue requested attribute value
     */
    private static void setOptionalFactoryAttribute(
            DocumentBuilderFactory factory,
            String attributeName,
            Object attributeValue) {

        try {
            factory.setAttribute(
                    attributeName,
                    attributeValue);

        } catch (IllegalArgumentException
                 | UnsupportedOperationException exception) {

            System.err.println(
                    DIAGNOSTIC_PREFIX
                            + "XML parser provider '"
                            + factory.getClass().getName()
                            + "' does not support optional attribute '"
                            + attributeName
                            + "'. Configuration loading continues. Cause: "
                            + exception.getMessage());
        }
    }

    /**
     * Parses all checker definitions from the XML document.
     *
     * @param document parsed XML document
     * @return ordered checker definitions
     */
    private static List<CheckerDefinition> parseDefinitions(
            Document document) {

        List<CheckerDefinition> parsedDefinitions =
                new ArrayList<>();

        NodeList checkerNodes =
                document.getElementsByTagName("checkers");

        for (int checkerIndex = 0;
             checkerIndex < checkerNodes.getLength();
             checkerIndex++) {

            Element checkerElement =
                    (Element) checkerNodes.item(checkerIndex);

            List<String> metaClasses =
                    new ArrayList<>();

            NodeList children =
                    checkerElement.getChildNodes();

            for (int childIndex = 0;
                 childIndex < children.getLength();
                 childIndex++) {

                Node child =
                        children.item(childIndex);

                if (child instanceof Element
                        && "metaClasses".equals(child.getNodeName())) {

                    String value =
                            child.getTextContent();

                    if (value != null
                            && !value.isBlank()) {

                        metaClasses.add(
                                value.trim());
                    }
                }
            }

            parsedDefinitions.add(
                    new CheckerDefinition(
                            checkerElement.getAttribute("id"),
                            checkerElement.getAttribute("javaClassName"),
                            checkerElement.getAttribute("domain"),
                            checkerElement.getAttribute("name"),
                            checkerElement.getAttribute("severity"),
                            parseBoolean(
                                    checkerElement.getAttribute("enabled"),
                                    true),
                            parseBoolean(
                                    checkerElement.getAttribute("completeness"),
                                    false),
                            parseBoolean(
                                    checkerElement.getAttribute("shouldCallFromCG"),
                                    false),
                            metaClasses));
        }

        if (parsedDefinitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "The checker configuration contains no <checkers> element.");
        }

        return parsedDefinitions;
    }

    /**
     * Parses an optional XML boolean attribute.
     *
     * @param value raw XML attribute value
     * @param defaultValue value used when the attribute is absent or blank
     * @return parsed boolean value
     */
    private static boolean parseBoolean(
            String value,
            boolean defaultValue) {

        return value == null
                || value.isBlank()
                ? defaultValue
                : Boolean.parseBoolean(value.trim());
    }

    /**
     * Locates a configuration file next to the class output location or JAR.
     *
     * @param anchorClass class used to locate the deployment directory
     * @param fileName configuration file name
     * @return resolved configuration path
     */
    private static Path locateNextToClass(
            Class<?> anchorClass,
            String fileName) {

        try {
            URI locationUri =
                    anchorClass
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI();

            Path location =
                    Path.of(locationUri)
                            .toAbsolutePath()
                            .normalize();

            Path directory =
                    Files.isDirectory(location)
                            ? location
                            : location.getParent();

            if (directory == null) {
                throw new IllegalStateException(
                        "Cannot determine the directory containing "
                                + anchorClass.getName());
            }

            Path directCandidate =
                    directory.resolve(fileName);

            if (Files.isRegularFile(directCandidate)) {
                return directCandidate;
            }

            Path configCandidate =
                    directory
                            .resolve("config")
                            .resolve(fileName);

            if (Files.isRegularFile(configCandidate)) {
                return configCandidate;
            }

            return directCandidate;

        } catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "Cannot resolve the plugin installation directory.",
                    exception);
        }
    }

    /**
     * Returns the configuration associated with a checker Java class.
     *
     * @param javaClassName fully qualified checker class name
     * @return matching checker definition
     */
    public CheckerDefinition findDefinitionByJavaClassName(
            String javaClassName) {

        CheckerDefinition definition =
                definitionsByClassName.get(javaClassName);

        if (definition == null) {
            throw new IllegalStateException(
                    "No configuration found for checker class "
                            + javaClassName);
        }

        return definition;
    }

    /**
     * Returns all configured checker definitions in XML declaration order.
     *
     * @return immutable checker definition list
     */
    public List<CheckerDefinition> getDefinitions() {
        return definitions;
    }

    /**
     * Returns the normalized path of the loaded XML configuration.
     *
     * @return XML configuration path
     */
    public Path getConfigurationPath() {
        return configurationPath;
    }
}
