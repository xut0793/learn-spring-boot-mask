package com.learn.mask.aop;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.testsupport.MaskingFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveObjectWalkerTest {

    private final MaskingProperties properties = new MaskingProperties();
    private final MaskEngine engine = MaskingFixtures.engine(properties);
    private final SensitiveObjectWalker walker = new SensitiveObjectWalker(
            engine, properties, new MaskContext(properties));

    static class User {
        String name = "张三";
        @Sensitive(type = SensitiveType.PHONE)
        String phone = "13812345678";
        @Sensitive(type = SensitiveType.EMAIL)
        String email = "alice@example.com";
        Contact contact = new Contact();
        List<Contact> contacts = new ArrayList<>(List.of(new Contact()));
    }

    static class Contact {
        String title = "紧急联系人";
        @Sensitive(type = SensitiveType.PHONE)
        String phone = "13900001111";
    }

    static class Person {
        @Sensitive(type = SensitiveType.PHONE)
        String phone = "13812345678";
    }

    static class Employee extends Person {
        String title = "工程师";
    }

    static class Node {
        @Sensitive(type = SensitiveType.PHONE)
        String phone = "13812345678";
        Node next;
    }

    @Test
    @DisplayName("就地改写：返回的是同一个对象，明文已经没了")
    void mutatesInPlace() {
        User user = new User();
        Object returned = walker.mask(user);

        assertThat(returned).isSameAs(user);
        assertThat(user.name).isEqualTo("张三");
        assertThat(user.phone).isEqualTo("138****5678");
        assertThat(user.email).isEqualTo("a****@example.com");
    }

    @Test
    @DisplayName("嵌套 Bean 和 List 里的 @Sensitive 也会被打码")
    void nestedBeanAndListAreMasked() {
        User user = new User();
        walker.mask(user);

        assertThat(user.contact.phone).isEqualTo("139****1111");
        assertThat(user.contacts.get(0).phone).isEqualTo("139****1111");
        assertThat(user.contact.title).isEqualTo("紧急联系人");
    }

    @Test
    @DisplayName("父类上的 @Sensitive 也会被走到")
    void walksSuperclassFields() {
        Employee employee = new Employee();
        walker.mask(employee);

        assertThat(employee.phone).isEqualTo("138****5678");
        assertThat(employee.title).isEqualTo("工程师");
    }

    @Test
    @DisplayName("循环引用不会 StackOverflow")
    void cycleDoesNotOverflow() {
        Node a = new Node();
        Node b = new Node();
        b.phone = "13900001111";
        a.next = b;
        b.next = a;

        walker.mask(a);

        assertThat(a.phone).isEqualTo("138****5678");
        assertThat(b.phone).isEqualTo("139****1111");
    }

    @Test
    @DisplayName("JDK 类型不反射 —— LocalDate 不会被拆开改字段")
    void skipsJdkTypes() {
        class Dated {
            LocalDate date = LocalDate.of(2024, 1, 1);
            @Sensitive(type = SensitiveType.PHONE)
            String phone = "13812345678";
        }
        Dated dated = new Dated();
        walker.mask(dated);
        assertThat(dated.date).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(dated.phone).isEqualTo("138****5678");
    }

    @Nested
    @DisplayName("Collection 必须先于 JDK 包跳过处理")
    class CollectionFirst {

        @Test
        @DisplayName("根对象是 List<Dto> 时也会打码 —— 不能因为 java.util 就整表跳过")
        void listOfDtoIsMasked() {
            List<Contact> contacts = new ArrayList<>(List.of(new Contact(), new Contact()));
            walker.mask(contacts);

            assertThat(contacts.get(0).phone).isEqualTo("139****1111");
            assertThat(contacts.get(1).phone).isEqualTo("139****1111");
        }

        @Test
        @DisplayName("数组同样会走进元素")
        void arrayOfDtoIsMasked() {
            Contact[] contacts = {new Contact()};
            walker.mask(contacts);
            assertThat(contacts[0].phone).isEqualTo("139****1111");
        }
    }

    @Nested
    @DisplayName("Map 按键名打码")
    class Maps {

        @Test
        @DisplayName("命中 map-keys 的字符串值就地改写，并递归嵌套 Map")
        void masksMappedKeysAndRecurses() {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("phone", "13900001111");
            nested.put("title", "紧急联系人");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("phone", "13812345678");
            data.put("name", "张三");
            data.put("contact", nested);

            walker.mask(data);

            assertThat(data.get("phone")).isEqualTo("138****5678");
            assertThat(data.get("name")).isEqualTo("张三");
            assertThat(((Map<?, ?>) data.get("contact")).get("phone")).isEqualTo("139****1111");
        }
    }
}
