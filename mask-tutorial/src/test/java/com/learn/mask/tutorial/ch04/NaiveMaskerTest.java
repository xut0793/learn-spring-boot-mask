package com.learn.mask.tutorial.ch04;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 把第 1 章和第 4 章讲的每个 bug 都断言出来。
 * <p>
 * 这些测试是**故意断言错误行为**的：它们绿了不代表代码对，而是证明「bug 确实存在」。
 * 这种测试在真实项目里也有用——发现 bug 时先写一个断言当前错误行为的测试，
 * 修完再把断言改成正确期望，就能确保修复真的生效了。
 */
class NaiveMaskerTest {

    @Test
    @DisplayName("replace 版：常见数据碰巧是对的")
    void replaceWorksByLuck() {
        assertThat(NaiveMasker.maskPhoneByReplace("13812345678")).isEqualTo("138****5678");
    }

    @Test
    @DisplayName("replace 版 bug：中间四位重复出现时替换了全部匹配")
    void replaceMasksEveryOccurrence() {
        // substring(3,7) == "1234"，而 "13812341234" 里有两处 "1234"
        assertThat(NaiveMasker.maskPhoneByReplace("13812341234"))
                .isEqualTo("138********")
                .isNotEqualTo("138****1234");
    }

    @Test
    @DisplayName("substring 版：替换位置对了，但脏数据会炸")
    void substringVersionBlowsUpOnDirtyData() {
        assertThat(NaiveMasker.maskPhoneBySubstring("13812341234")).isEqualTo("138****1234");

        assertThatThrownBy(() -> NaiveMasker.maskPhoneBySubstring("138"))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> NaiveMasker.maskPhoneBySubstring(""))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> NaiveMasker.maskPhoneBySubstring(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("if-else 版：功能是对的，问题在结构")
    void ifElseVersionWorksButDoesNotScale() {
        assertThat(NaiveMasker.maskByType("phone", "13812345678")).isEqualTo("138****5678");
        assertThat(NaiveMasker.maskByType("idCard", "110101199003078515")).isEqualTo("110101********8515");
        assertThat(NaiveMasker.maskByType("email", "zhangsan@example.com")).isEqualTo("z*******@example.com");
        // 未知类型落到最后的兜底分支
        assertThat(NaiveMasker.maskByType("passport", "E12345678")).isEqualTo("E*******8");
    }
}
