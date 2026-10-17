public class DIP {

    public static void main(String[] args) {

        System.out.println("=== BEFORE DIP ===");

        BeforeEmailService oldService = new BeforeEmailService();
        oldService.sendNotification();

        System.out.println("\n=== AFTER DIP ===");

        MessageSender email = new EmailSender();
        NotificationService emailService = new NotificationService(email);

        emailService.notifyUser();

        MessageSender sms = new SmsSender();
        NotificationService smsService = new NotificationService(sms);

        smsService.notifyUser();
    }

    /*
     * BEFORE:
     * High-level business logic directly depends
     * on a specific low-level implementation.
     */
    static class BeforeEmailService {

        private final BeforeEmailSender sender = new BeforeEmailSender();

        void sendNotification() {
            sender.sendEmail();
        }
    }

    static class BeforeEmailSender {

        void sendEmail() {
            System.out.println("Sending email notification");
        }
    }

    /*
     * AFTER:
     * Both the notification service and its implementations
     * work through a common abstraction.
     */
    interface MessageSender {
        void send(String message);
    }

    // Low-level implementation: email delivery.
    static class EmailSender implements MessageSender {

        @Override
        public void send(String message) {
            System.out.println("Email: " + message);
        }
    }

    // Another low-level implementation: SMS delivery.
    static class SmsSender implements MessageSender {

        @Override
        public void send(String message) {
            System.out.println("SMS: " + message);
        }
    }

    // High-level business logic.
    static class NotificationService {

        private final MessageSender sender;

        // Dependency is provided from outside.
        NotificationService(MessageSender sender) {
            this.sender = sender;
        }

        void notifyUser() {
            sender.send("Your order has been confirmed.");
        }
    }
}

/*
 * EXPECTED OUTPUT
 * 
 * === BEFORE DIP ===
 * Sending email notification
 * 
 * === AFTER DIP ===
 * Email: Your order has been confirmed.
 * SMS: Your order has been confirmed.
 */