package org.hesperides.core.application.platforms;

import org.apache.commons.lang3.StringUtils;
import org.hesperides.core.domain.exceptions.ForbiddenOperationException;
import org.hesperides.core.domain.modules.entities.Module;
import org.hesperides.core.domain.modules.exceptions.ModuleNotFoundException;
import org.hesperides.core.domain.modules.queries.ModuleQueries;
import org.hesperides.core.domain.modules.queries.ModuleSimplePropertiesView;
import org.hesperides.core.domain.platforms.commands.PlatformCommands;
import org.hesperides.core.domain.platforms.entities.Platform;
import org.hesperides.core.domain.platforms.entities.properties.AbstractValuedProperty;
import org.hesperides.core.domain.platforms.entities.properties.ValuedProperty;
import org.hesperides.core.domain.platforms.exceptions.ApplicationNotFoundException;
import org.hesperides.core.domain.platforms.exceptions.DuplicatePlatformException;
import org.hesperides.core.domain.platforms.exceptions.PlatformNotFoundException;
import org.hesperides.core.domain.platforms.queries.PlatformQueries;
import org.hesperides.core.domain.platforms.queries.views.*;
import org.hesperides.core.domain.platforms.queries.views.properties.AbstractValuedPropertyView;
import org.hesperides.core.domain.platforms.queries.views.properties.GlobalPropertyUsageView;
import org.hesperides.core.domain.platforms.queries.views.properties.ValuedPropertyView;
import org.hesperides.core.domain.security.User;
import org.hesperides.core.domain.templatecontainers.entities.TemplateContainer;
import org.hesperides.core.domain.templatecontainers.queries.AbstractPropertyView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hesperides.core.domain.platforms.queries.views.DeployedModuleView.toDomainDeployedModules;
import static org.hesperides.core.domain.platforms.queries.views.properties.AbstractValuedPropertyView.hidePasswordProperties;
import static org.hesperides.core.domain.platforms.queries.views.properties.ValuedPropertyView.toDomainValuedProperties;


@Component
public class PlatformUseCases {

    public static final String ROOT_PATH = "#";
    private final PlatformCommands commands;
    private final PlatformQueries queries;
    private final ModuleQueries moduleQueries;

    @Autowired
    public PlatformUseCases(PlatformCommands commands, PlatformQueries queries, final ModuleQueries moduleQueries) {
        this.commands = commands;
        this.queries = queries;
        this.moduleQueries = moduleQueries;
    }

    public String createPlatform(Platform platform, User user) {
        if (platform.isProductionPlatform() && !user.isProd()) {
            throw new ForbiddenOperationException("Creating a production platform is reserved to production role");
        }
        if (queries.platformExists(platform.getKey())) {
            throw new DuplicatePlatformException(platform.getKey());
        }
        return commands.createPlatform(platform, user);
    }

    public String copyPlatform(Platform newPlatform, Platform.Key existingPlatformKey, User user) {
        if (queries.platformExists(newPlatform.getKey())) {
            throw new DuplicatePlatformException(newPlatform.getKey());
        }
        PlatformView existingPlatform = queries.getOptionalPlatform(existingPlatformKey)
                .orElseThrow(() -> new PlatformNotFoundException(existingPlatformKey));
        if ((newPlatform.isProductionPlatform() || existingPlatform.isProductionPlatform()) && !user.isProd()) {
            throw new ForbiddenOperationException("Creating a platform from a production platform is reserved to production role");
        }
        // cf. createPlatformFromExistingPlatform in https://github.com/voyages-sncf-technologies/hesperides/blob/fix/3.0.3/src/main/java/com/vsct/dt/hesperides/applications/AbstractApplicationsAggregate.java#L156
        Platform newFullPlatform = new Platform(
                newPlatform.getKey(),
                newPlatform.getVersion(),
                newPlatform.isProductionPlatform(),
                1L,
                toDomainDeployedModules(existingPlatform.getDeployedModules()),
                toDomainValuedProperties(existingPlatform.getGlobalProperties())
        );
        return commands.createPlatform(newFullPlatform, user);
    }

    public PlatformView getPlatform(String platformId) {
        return queries.getOptionalPlatform(platformId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));
    }

    public PlatformView getPlatform(Platform.Key platformKey) {
        return queries.getOptionalPlatform(platformKey)
                .orElseThrow(() -> new PlatformNotFoundException(platformKey));
    }

    public void updatePlatform(Platform.Key platformKey, Platform newPlatform, boolean copyPropertiesForUpgradedModules, User user) {
        PlatformView existingPlatform = queries.getOptionalPlatform(platformKey)
                .orElseThrow(() -> new PlatformNotFoundException(platformKey));
        if (!user.isProd()) {
            if (existingPlatform.isProductionPlatform() && newPlatform.isProductionPlatform()) {
                throw new ForbiddenOperationException("Updating a production platform is reserved to production role");
            }
            if (existingPlatform.isProductionPlatform() || newPlatform.isProductionPlatform()) {
                throw new ForbiddenOperationException("Upgrading a platform to production is reserved to production role");
            }
        }
        commands.updatePlatform(existingPlatform.getId(), newPlatform, copyPropertiesForUpgradedModules, user);
    }

    public void deletePlatform(Platform.Key platformKey, User user) {
        PlatformView platform = queries.getOptionalPlatform(platformKey)
                .orElseThrow(() -> new PlatformNotFoundException(platformKey));
        if (platform.isProductionPlatform() && !user.isProd()) {
            throw new ForbiddenOperationException("Deleting a production platform is reserved to production role");
        }
        commands.deletePlatform(platform.getId(), user);
    }

    public ApplicationView getApplication(String applicationName) {
        return queries.getApplication(applicationName)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationName));
    }

    public List<ModulePlatformView> getPlatformUsingModule(Module.Key moduleKey) {
        return queries.getPlatformsUsingModule(moduleKey);
    }

    public List<SearchPlatformResultView> searchPlatforms(String applicationName, String platformName) {
        return queries.searchPlatforms(applicationName, platformName);
    }

    public List<SearchApplicationResultView> searchApplications(String applicationName) {
        return queries.searchApplications(applicationName);
    }

    public List<AbstractValuedPropertyView> getProperties(final Platform.Key platformKey, final String propertiesPath, final User user) {
        List<AbstractValuedPropertyView> properties = new ArrayList<>();

        PlatformView platform = queries.getOptionalPlatform(platformKey)
                .orElseThrow(() -> new PlatformNotFoundException(platformKey));

        if (ROOT_PATH.equals(propertiesPath)) {
            properties.addAll(queries.getGlobalProperties(platformKey));
        } else if (StringUtils.isNotEmpty(propertiesPath)) {
            final Module.Key moduleKey = Module.Key.fromPropertiesPath(propertiesPath);
            if (!moduleQueries.moduleExists(moduleKey)) {
                throw new ModuleNotFoundException(moduleKey);
            }
            List<AbstractValuedPropertyView> deployedModuleProperties = queries.getDeployedModuleProperties(platformKey, propertiesPath);
            if (platform.isProductionPlatform() && !user.isProd()) {
                deployedModuleProperties = hidePasswordProperties(deployedModuleProperties);
            }
            properties.addAll(deployedModuleProperties);
            properties.addAll(getGlobalPropertiesUsedInModule(platform, propertiesPath, moduleKey));
        }
        return properties;
    }

    private List<ValuedPropertyView> getGlobalPropertiesUsedInModule(PlatformView platform, String propertiesPath, Module.Key moduleKey) {
            List<AbstractPropertyView> flatModuleProperties = AbstractPropertyView.flattenProperties(moduleQueries.getProperties(moduleKey));
            return platform.getGlobalProperties().stream().filter(globalProperty -> {
                List<GlobalPropertyUsageView> moduleGlobalProperties = GlobalPropertyUsageView.getModuleGlobalProperties(
                        flatModuleProperties, globalProperty.getName(), propertiesPath);
                return !CollectionUtils.isEmpty(moduleGlobalProperties);
            }).collect(Collectors.toList());
    }

    public Map<String, Set<GlobalPropertyUsageView>> getGlobalPropertiesUsage(final Platform.Key platformKey) {
        PlatformView platform = queries.getOptionalPlatform(platformKey).orElseThrow(() -> new PlatformNotFoundException(platformKey));
        List<TemplateContainer.Key> modulesKeys = platform.getDeployedModules()
                .stream()
                .map(DeployedModuleView::getModuleKey)
                .collect(Collectors.toList());
        List<ModuleSimplePropertiesView> modulesSimpleProperties = moduleQueries.getModulesSimpleProperties(modulesKeys);

        return platform.getGlobalProperties().stream()
                .map(ValuedPropertyView::getName)
                .collect(Collectors.toMap(Function.identity(), globalPropertyName ->
                        GlobalPropertyUsageView.getGlobalPropertyUsage(globalPropertyName, platform.getDeployedModules(), modulesSimpleProperties)));
    }

    public List<String> getInstancesModel(final Platform.Key platformKey, final String propertiesPath) {
        if (!queries.platformExists(platformKey)) {
            throw new PlatformNotFoundException(platformKey);
        }
        return queries.getInstancesModel(platformKey, propertiesPath);
    }

    public List<AbstractValuedPropertyView> saveProperties(final Platform.Key platformKey,
                                                           final String propertiesPath,
                                                           final Long platformVersionId,
                                                           final List<AbstractValuedProperty> abstractValuedProperties,
                                                           final User user) {
        Optional<String> platformId = queries.getOptionalPlatformId(platformKey);
        if (!platformId.isPresent()) {
            throw new PlatformNotFoundException(platformKey);
        }
        if (ROOT_PATH.equals(propertiesPath)) {
            List<ValuedProperty> valuedProperties = AbstractValuedProperty.filterAbstractValuedPropertyWithType(abstractValuedProperties, ValuedProperty.class);
            // Platform properties are global and should always be of type ValuedProperty
            if (valuedProperties.size() != abstractValuedProperties.size()) {
                throw new IllegalArgumentException("Global properties should always be valued properties");
            }
            commands.savePlatformProperties(platformId.get(), platformVersionId, valuedProperties, user);
        } else {
            final Module.Key moduleKey = Module.Key.fromPropertiesPath(propertiesPath);
            if (!moduleQueries.moduleExists(moduleKey)) {
                throw new ModuleNotFoundException(moduleKey);
            }
            commands.saveModulePropertiesInPlatform(platformId.get(), propertiesPath, platformVersionId, abstractValuedProperties, user);
        }

        return getProperties(platformKey, propertiesPath, user);
    }
}