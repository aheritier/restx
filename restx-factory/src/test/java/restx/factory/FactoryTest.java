package restx.factory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * User: xavierhanin
 * Date: 1/31/13
 * Time: 7:11 PM
 */
public class FactoryTest {

    @Test
    public void should_build_new_component_from_single_machine() throws Exception {
        Factory factory = Factory.builder().addMachine(testMachine()).build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test")).findOne();

        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getName()).isEqualTo(Name.of(String.class, "test"));
        assertThat(component.get().getComponent()).isEqualTo("value1");

        assertThat(factory.queryByName(Name.of(String.class, "test")).findOne().get()).isEqualTo(component.get());
    }

    @Test
    public void should_build_new_component_with_deps() throws Exception {
        Factory factory = Factory.builder()
                .addMachine(testMachine())
                .addMachine(new SingleNameFactoryMachine<>(
                        0, new StdMachineEngine<String>(Name.of(String.class, "test2"), BoundlessComponentBox.FACTORY) {
                    private Factory.Query<String> stringQuery = Factory.Query.byName(Name.of(String.class, "test"));

                    @Override
                    public BillOfMaterials getBillOfMaterial() {
                        return BillOfMaterials.of(stringQuery);
                    }

                    @Override
                    protected String doNewComponent(SatisfiedBOM satisfiedBOM) {
                        return satisfiedBOM.getOne(stringQuery).get().getComponent() + " value2";
                    }
                }))
                .build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test2")).findOne();

        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getName()).isEqualTo(Name.of(String.class, "test2"));
        assertThat(component.get().getComponent()).isEqualTo("value1 value2");

        assertThat(factory.queryByName(Name.of(String.class, "test2")).findOne().get()).isEqualTo(component.get());
    }

    @Test
    public void should_fail_with_missing_deps() throws Exception {
        SingleNameFactoryMachine<String> machine = machineWithMissingDependency();
        Factory factory = Factory.builder().addMachine(machine).build();

        try {
            factory.queryByName(Name.of(String.class, "test")).findOne();
            fail("should raise exception when asking for a component with missing dependency");
        } catch (IllegalStateException e) {
            assertThat(e)
                    .hasMessageStartingWith(
                            "Name{name='test', clazz=class java.lang.String}\n" +
                                    "  -> Name{name='missing', clazz=class java.lang.String} can't be satisfied")
                    .hasMessageContaining(machine.toString())
            ;
        }
    }

    @Test
    public void should_warn_about_missing_annotated_machine() throws Exception {
        Factory factory = Factory.builder().addFromServiceLoader().build();

        assertThat(factory.dump()).contains(TestMissingAnnotatedMachine.class.getName());
    }

    @Test
    public void should_dump_list_overrider() throws Exception {
        SingleNameFactoryMachine<String> machine1 = testMachine();
        SingleNameFactoryMachine<String> machine2 = overridingMachine();
        Factory factory = Factory.builder()
                .addMachine(machine1)
                .addMachine(machine2)
                .build();

        assertThat(factory.dump()).contains("OVERRIDING:\n         " + machine1);
    }

    @Test
    public void should_customize_component() throws Exception {
        Factory factory = Factory.builder()
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(
                        String.class, "test", "hello")))
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(ComponentCustomizerEngine.class, "cutomizerTest",
                        new ComponentCustomizerEngine() {
                    @Override
                    public <T> boolean canCustomize(Name<T> name) {
                        return name.getClazz() == String.class;
                    }

                    @Override
                    public <T> ComponentCustomizer<T> getCustomizer(Name<T> name) {
                        return new ComponentCustomizer<T>() {
                            @Override
                            public int priority() {
                                return 0;
                            }

                            @Override
                            public NamedComponent<T> customize(NamedComponent<T> namedComponent) {
                                return new NamedComponent<>(
                                        namedComponent.getName(), (T) (namedComponent.getComponent() + " world"));
                            }
                        };
                    }
                })))
                .build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test")).findOne();

        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getName()).isEqualTo(Name.of(String.class, "test"));
        assertThat(component.get().getComponent()).isEqualTo("hello world");

        assertThat(factory.queryByName(Name.of(String.class, "test")).findOne().get()).isEqualTo(component.get());
    }

    @Test
    public void should_customize_component_with_simple_customizer() throws Exception {
        Factory factory = Factory.builder()
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(
                        String.class, "test", "hello")))
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(ComponentCustomizerEngine.class, "cutomizerTest",
                        new SingleComponentClassCustomizerEngine<String>(0, String.class) {
                            @Override
                            public NamedComponent<String> customize(NamedComponent<String> namedComponent) {
                                return new NamedComponent<>(
                                        namedComponent.getName(), namedComponent.getComponent() + " world");
                            }
                })))
                .build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test")).findOne();

        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getComponent()).isEqualTo("hello world");
    }


    @Test
    public void should_customize_component_with_customizer_with_deps() throws Exception {
        Factory factory = Factory.builder()
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(
                        String.class, "dep", "world")))
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(
                        String.class, "test", "hello")))
                .addMachine(new SingleNameFactoryMachine<>(0, new StdMachineEngine<ComponentCustomizerEngine>(
                        Name.of(ComponentCustomizerEngine.class, "cutomizerTest"), BoundlessComponentBox.FACTORY) {
                    private Factory.Query<String> query = Factory.Query.byName(Name.of(String.class, "dep"));

                    @Override
                    public BillOfMaterials getBillOfMaterial() {
                        return BillOfMaterials.of(query);
                    }

                    @Override
                    protected ComponentCustomizerEngine doNewComponent(final SatisfiedBOM satisfiedBOM) {
                        return new SingleComponentClassCustomizerEngine<String>(0, String.class) {
                            @Override
                            public NamedComponent<String> customize(NamedComponent<String> namedComponent) {
                                return new NamedComponent<>(
                                        namedComponent.getName(), namedComponent.getComponent()
                                            + " " + satisfiedBOM.getOne(query).get().getComponent());
                            }
                        };
                    }
                }))
                .build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test")).findOne();
        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getComponent()).isEqualTo("hello world");
    }

    @Test
    public void should_handle_machine_factory_to_build_conditional_component() throws Exception {
        SingleNameFactoryMachine<FactoryMachine> alternativeMachine = alternativeMachine();

        Factory factory = Factory.builder()
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(String.class, "mode", "dev")))
                .addMachine(alternativeMachine)
                .build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test")).optional().findOne();
        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getComponent()).isEqualTo("hello");

        factory = Factory.builder()
                        .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(String.class, "mode", "prod")))
                        .addMachine(alternativeMachine)
                        .build();

        component = factory.queryByName(Name.of(String.class, "test")).optional().findOne();
        assertThat(component.isPresent()).isFalse();
    }

    @Test
    public void should_handle_machine_factory_to_build_alternative() throws Exception {
        SingleNameFactoryMachine<FactoryMachine> alternativeMachine = alternativeMachine();

        Factory factory = Factory.builder()
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(String.class, "mode", "dev")))
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(String.class, "test", "default")))
                .addMachine(alternativeMachine)
                .build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test")).optional().findOne();
        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getComponent()).isEqualTo("hello");

        factory = Factory.builder()
                        .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(String.class, "mode", "prod")))
                        .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(String.class, "test", "default")))
                        .addMachine(alternativeMachine)
                        .build();

        component = factory.queryByName(Name.of(String.class, "test")).optional().findOne();
        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getComponent()).isEqualTo("default");
    }

    @Test
    public void should_handle_machine_factory_with_dependencies_on_other_machine_factory() throws Exception {
        SingleNameFactoryMachine<FactoryMachine> alternativeMachine = alternativeMachine();
        SingleNameFactoryMachine<FactoryMachine> dependentMachine = new SingleNameFactoryMachine<>(0, new StdMachineEngine<FactoryMachine>(
                Name.of(FactoryMachine.class, "machineFactoryTest2"), BoundlessComponentBox.FACTORY) {
            private Factory.Query<String> query = Factory.Query.byName(Name.of(String.class, "test"));

            @Override
            public BillOfMaterials getBillOfMaterial() {
                return BillOfMaterials.of(query);
            }

            @Override
            protected FactoryMachine doNewComponent(final SatisfiedBOM satisfiedBOM) {
                return new SingletonFactoryMachine<>(0, NamedComponent.of(
                        String.class, "test2", satisfiedBOM.getOne(query).get().getComponent() + " world"));
            }
        });

        Factory factory = Factory.builder()
                .addMachine(new SingletonFactoryMachine<>(0, NamedComponent.of(String.class, "mode", "dev")))
                .addMachine(alternativeMachine)
                .addMachine(dependentMachine)
                .build();

        Optional<NamedComponent<String>> component = factory.queryByName(Name.of(String.class, "test2")).optional().findOne();
        assertThat(component.isPresent()).isTrue();
        assertThat(component.get().getComponent()).isEqualTo("hello world");
    }

    private SingleNameFactoryMachine<FactoryMachine> alternativeMachine() {
        return new AlternativesFactoryMachine<>(0, Name.of(String.class, "mode"),
                ImmutableMap.of("dev", new SingletonFactoryMachine<>(0, NamedComponent.of(
                        String.class, "test", "hello"))), BoundlessComponentBox.FACTORY);
    }

    @Test
    public void should_build_component_lists_from_multiple_machines() throws Exception {
        Factory factory = Factory.builder()
                .addMachine(new SingleNameFactoryMachine(
                        0, new NoDepsMachineEngine<String>(Name.of(String.class, "test 1"), BoundlessComponentBox.FACTORY) {
                    @Override
                    protected String doNewComponent(SatisfiedBOM satisfiedBOM) {
                        return "value 1";
                    }
                }))
                .addMachine(new SingleNameFactoryMachine(
                        0, new NoDepsMachineEngine<String>(Name.of(String.class, "test 2"), BoundlessComponentBox.FACTORY) {
                    @Override
                    protected String doNewComponent(SatisfiedBOM satisfiedBOM) {
                        return "value 2";
                    }
                }))
                .build();

        Set<NamedComponent<String>> components = factory.queryByClass(String.class).find();

        assertThat(components).containsExactly(
                NamedComponent.of(String.class, "test 1", "value 1"),
                NamedComponent.of(String.class, "test 2", "value 2"));
    }

    @Test
    public void should_factory_be_queryable() throws Exception {
        Factory factory = Factory.builder().build();

        assertThat(factory.queryByClass(Factory.class).findAsComponents()).containsExactly(factory);
    }

    @Test
    public void should_allow_to_close_with_factory_queried() throws Exception {
        // check that we don't get a stack overflow error due to box closing the factory
        Factory factory = Factory.builder().build();

        assertThat(factory.queryByClass(Factory.class).findAsComponents()).containsExactly(factory);

        factory.close();
    }

    private SingleNameFactoryMachine<String> testMachine() {
        return new SingleNameFactoryMachine<>(
                0, new NoDepsMachineEngine<String>(Name.of(String.class, "test"), BoundlessComponentBox.FACTORY) {
            @Override
            protected String doNewComponent(SatisfiedBOM satisfiedBOM) {
                return "value1";
            }
        });
    }

    private SingleNameFactoryMachine<String> machineWithMissingDependency() {
        return new SingleNameFactoryMachine<>(
                0, new StdMachineEngine<String>(Name.of(String.class, "test"), BoundlessComponentBox.FACTORY) {
            @Override
            public BillOfMaterials getBillOfMaterial() {
                return BillOfMaterials.of(Factory.Query.byName(Name.of(String.class, "missing")));
            }

            @Override
            protected String doNewComponent(SatisfiedBOM satisfiedBOM) {
                throw new RuntimeException("shouldn't be called");
            }
        });
    }

    private SingleNameFactoryMachine<String> overridingMachine() {
        return new SingleNameFactoryMachine<>(
                -10, new NoDepsMachineEngine<String>(Name.of(String.class, "test"), BoundlessComponentBox.FACTORY) {
            @Override
            protected String doNewComponent(SatisfiedBOM satisfiedBOM) {
                return "value1";
            }
        });
    }
}
