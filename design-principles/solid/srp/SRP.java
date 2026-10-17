/*

User Class represents user data object. UserRepository saves the user to DB while EmailService sends email. If one email provider chanegs, it has no impact on user.

*/

public class SRP {

    public static void main(String[] args) {
        User user = new User("Kartikeya", "kartikeya@example.com");

        UserRepository repository = new UserRepository();
        EmailService emailService = new EmailService();

        repository.save(user);
        emailService.sendWelcomeEmail(user);
    }
}

class User {

    String name;
    String email;

    User(String name, String email) {
        this.name = name;
        this.email = email;
    }
}

// Handles user data and persistence
class UserRepository {

    void save(User user) {
        System.out.println("Saving user: " + user.name);
    }
}

// Handles email delivery
class EmailService {

    void sendWelcomeEmail(User user) {
        System.out.println("Sending email to: " + user.email);
    }
}
