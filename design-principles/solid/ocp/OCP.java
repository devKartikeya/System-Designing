public class OCP {

    public static void main(String[] args) {

        System.out.println("=== BEFORE OCP ===");

        BeforePayment oldPayment = new BeforePayment();
        oldPayment.pay("UPI", 500);
        oldPayment.pay("CARD", 1000);
        oldPayment.pay("NET_BANKING", 2000);

        System.out.println("\n=== AFTER OCP ===");

        PaymentProcessor processor = new PaymentProcessor();

        processor.process(new UpiPayment(), 500);
        processor.process(new CardPayment(), 1000);
        processor.process(new NetBankingPayment(), 2000);
        processor.process(new WalletPayment(), 300);
    }

    // BEFORE: New payment methods require modifying this class.
    static class BeforePayment {

        void pay(String type, double amount) {
            if (type.equals("UPI")) {
                System.out.println("Paid ₹" + amount + " using UPI");
            } else if (type.equals("CARD")) {
                System.out.println("Paid ₹" + amount + " using Card");
            } else if (type.equals("NET_BANKING")) {
                System.out.println("Paid ₹" + amount + " using Net Banking");
            }
        }
    }

    // AFTER: Every payment method follows this contract.
    interface PaymentMethod {

        void pay(double amount);
    }

    static class UpiPayment implements PaymentMethod {

        public void pay(double amount) {
            System.out.println("Paid ₹" + amount + " using UPI");
        }
    }

    static class CardPayment implements PaymentMethod {

        public void pay(double amount) {
            System.out.println("Paid ₹" + amount + " using Card");
        }
    }

    static class NetBankingPayment implements PaymentMethod {

        public void pay(double amount) {
            System.out.println("Paid ₹" + amount + " using Net Banking");
        }
    }

    // Extension: add a new class; don't change the processor.
    static class WalletPayment implements PaymentMethod {

        public void pay(double amount) {
            System.out.println("Paid ₹" + amount + " using Wallet");
        }
    }

    static class PaymentProcessor {

        void process(PaymentMethod method, double amount) {
            method.pay(amount);
        }
    }
}
