
public class ISP {

    public static void main(String[] args) {

        System.out.println("=== BEFORE ISP ===");

        BeforePrinter basicPrinter = new BasicPrinter();
        basicPrinter.print();

        // BasicPrinter is forced to implement methods
        // that its device cannot actually support.
        basicPrinter.scan();

        System.out.println("\n=== AFTER ISP ===");

        Printer printer = new SimplePrinter();
        printer.print();

        MultiFunctionPrinter officePrinter = new OfficePrinter();

        officePrinter.print();
        officePrinter.scan();
        officePrinter.fax();
    }

    /*
     * BEFORE:
     * One large interface forces every printer to support
     * printing, scanning, and faxing.
     */
    interface BeforePrinter {
        void print();

        void scan();

        void fax();
    }

    static class BasicPrinter implements BeforePrinter {

        @Override
        public void print() {
            System.out.println("Printing document");
        }

        @Override
        public void scan() {
            throw new UnsupportedOperationException(
                    "Scanning is not supported");
        }

        @Override
        public void fax() {
            throw new UnsupportedOperationException(
                    "Faxing is not supported");
        }
    }

    /*
     * AFTER:
     * Small interfaces represent separate capabilities.
     */
    interface Printer {
        void print();
    }

    interface Scanner {
        void scan();
    }

    interface FaxMachine {
        void fax();
    }

    // This device only prints.
    static class SimplePrinter implements Printer {

        @Override
        public void print() {
            System.out.println("Simple printer: Printing document");
        }
    }

    // This device supports all three capabilities.
    interface MultiFunctionPrinter
            extends Printer, Scanner, FaxMachine {
    }

    static class OfficePrinter implements MultiFunctionPrinter {

        @Override
        public void print() {
            System.out.println("Office printer: Printing document");
        }

        @Override
        public void scan() {
            System.out.println("Office printer: Scanning document");
        }

        @Override
        public void fax() {
            System.out.println("Office printer: Sending fax");
        }
    }
}

/*
 * EXPECTED OUTPUT
 * 
 * === BEFORE ISP ===
 * Printing document
 * Exception in thread "main" java.lang.UnsupportedOperationException:
 * Scanning is not supported
 * 
 * The program stops here because the exception is not handled.
 * 
 * Comment out the BEFORE call to basicPrinter.scan()
 * to see the AFTER section run.
 */