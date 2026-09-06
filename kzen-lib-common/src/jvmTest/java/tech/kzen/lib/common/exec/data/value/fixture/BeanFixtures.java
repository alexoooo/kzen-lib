package tech.kzen.lib.common.exec.data.value.fixture;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;


/** Plain Java classes for the bean-shape convention tests: nothing here knows about kzen. */
public final class BeanFixtures {
    private BeanFixtures() {}


    public enum Side { BUY, SELL }


    public static class Person {
        public static final String KIND = "person";
        public String note = "n/a";
        private final String name;
        private final int age;

        public Person(String name, int age) { this.name = name; this.age = age; }

        public String getName() { return name; }
        public int getAge() { return age; }
        public boolean isActive() { return age < 65; }
        public String getURL() { return "https://example.test/" + name; }
        public Boolean isBoxed() { return Boolean.TRUE; }
        public String get() { return "not a property"; }
        public String is() { return "not a property"; }
        public static String getStatic() { return "excluded"; }
        public void getNothing() {}
        public String describe() { return "not a getter"; }
    }


    public static class Employee extends Person {
        public String note = "employee note";
        private final String department;

        public Employee(String name, int age, String department) { super(name, age); this.department = department; }

        @Override public String getName() { return "employee:" + super.getName(); }
        public String getDepartment() { return department; }
        public Side getSide() { return Side.BUY; }
        public Set<String> getTags() { return Set.of("a", "b"); }
        public List<Person> getReports() { return List.of(); }
    }


    public static class Conflicting {
        public int value = 1;
        public String getValue() { return "one"; }
    }


    public static class Throwing {
        public String getFine() { return "fine"; }
        public String getBroken() { throw new IllegalStateException("broken getter"); }
    }


    public static class Annotated {
        public @Nullable String maybe = null;
        public @NonNull String surely = "yes";
        public String unknown = "platform";

        public @Nullable String getOptional() { return null; }
        public @NonNull String getRequired() { return "required"; }
        public String getUnannotated() { return "u"; }
        public int getPrimitive() { return 1; }
    }


    @NullMarked
    public static class Marked {
        public String getRequired() { return "required"; }
        public @Nullable String getOptional() { return null; }
    }


    public static class Empty {
        public void run() {}
    }
}
