package uz.khakimjanovich.platform.persistence;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

class ModulePersistenceRegistrar implements ImportBeanDefinitionRegistrar {

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
    var module =
        Objects.requireNonNull(
            AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(EnableModulePersistence.class.getName())));
    var schema = module.getString("schema");
    var dataSource = schema + PersistenceConstants.DATA_SOURCE;
    var liquibase = schema + PersistenceConstants.LIQUIBASE;
    var entityManagerFactory = schema + PersistenceConstants.ENTITY_MANAGER_FACTORY;

    register(
        registry,
        dataSource,
        BeanDefinitionBuilder.rootBeanDefinition(ModulePersistenceRegistrar.class, "dataSource")
            .addConstructorArgValue(schema)
            .addConstructorArgValue(module.getString("prefix"))
            .addConstructorArgReference(PersistenceConstants.ENVIRONMENT_BEAN));
    register(
        registry,
        liquibase,
        BeanDefinitionBuilder.rootBeanDefinition(ModulePersistence.class, "liquibase")
            .addConstructorArgValue(schema)
            .addConstructorArgValue(module.getString("changelog"))
            .addConstructorArgReference(dataSource));
    register(
        registry,
        entityManagerFactory,
        BeanDefinitionBuilder.rootBeanDefinition(ModulePersistence.class, "entityManagerFactory")
            .addConstructorArgValue(schema)
            .addConstructorArgValue(module.getClass("entities"))
            .addConstructorArgReference(dataSource)
            .addDependsOn(liquibase));
    register(
        registry,
        schema + PersistenceConstants.TRANSACTION_MANAGER,
        BeanDefinitionBuilder.rootBeanDefinition(ModulePersistence.class, "transactionManager")
            .addConstructorArgReference(entityManagerFactory));
  }

  static HikariDataSource dataSource(String schema, String prefix, Environment environment) {
    var binder = Binder.get(environment);
    var pool =
        ModulePersistence.dataSource(
            schema, binder.bindOrCreate(prefix, DataSourceProperties.class));
    binder.bind(prefix + PersistenceConstants.HIKARI_SUFFIX, Bindable.ofInstance(pool));
    return pool;
  }

  private static void register(
      BeanDefinitionRegistry registry, String name, BeanDefinitionBuilder builder) {
    registry.registerBeanDefinition(name, builder.getBeanDefinition());
  }
}
