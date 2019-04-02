package es.andrewazor.containertest.tui;

class TtyClientWriter implements ClientWriter {
    @Override
    public void print(String s) {
        System.out.print(s);
    }

    @Override
    public void print(char c) {
        System.out.print(c);
    }

    @Override
    public void println(String s) {
        System.out.println(s);
    }

    @Override
    public void println() {
        System.out.println();
    }
}