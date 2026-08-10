package cn.longer233.gamenarrator.mobile;

public final class ExportStateMachine {
    public enum State {
        IDLE, PREPARING, RUNNING, PAUSED, CANCELING, CANCELLED, FAILED, COMPLETED;

        public boolean isTerminal() {
            return this == CANCELLED || this == FAILED || this == COMPLETED;
        }
    }

    private State state = State.IDLE;
    private String error = "";

    public State state() { return state; }
    public String error() { return error; }
    public boolean isTerminal() { return state.isTerminal(); }

    public void prepare() { require(State.IDLE); state = State.PREPARING; }
    public void run() { require(State.PREPARING); state = State.RUNNING; }
    public void pause() { require(State.RUNNING); state = State.PAUSED; }
    public void resume() { require(State.PAUSED); state = State.RUNNING; }

    public void cancel() {
        require(State.RUNNING, State.PAUSED);
        state = State.CANCELING;
    }

    public void cancelled() {
        require(State.CANCELING);
        state = State.CANCELLED;
    }

    public void complete() {
        require(State.RUNNING, State.CANCELING);
        error = "";
        state = State.COMPLETED;
    }

    public void fail(String message) {
        require(State.PREPARING, State.RUNNING, State.CANCELING);
        error = message == null ? "" : message;
        state = State.FAILED;
    }

    public void reset() {
        if (!state.isTerminal()) throw new IllegalStateException("只有终态可以重置");
        state = State.IDLE;
        error = "";
    }

    private void require(State... allowed) {
        if (state.isTerminal()) throw new IllegalStateException("终态不可覆盖：" + state);
        for (State value : allowed) {
            if (value == state) return;
        }
        throw new IllegalStateException("非法状态转换：" + state);
    }
}
