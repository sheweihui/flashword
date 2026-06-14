import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateBCrypt {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 生成 123456 的 BCrypt 哈希
        String password = "123456";
        String hashed = encoder.encode(password);
        
        System.out.println("明文密码: " + password);
        System.out.println("BCrypt哈希: " + hashed);
        System.out.println("\nSQL更新语句:");
        System.out.println("UPDATE user SET password = '" + hashed + "' WHERE username = 'test_user';");
    }
}
