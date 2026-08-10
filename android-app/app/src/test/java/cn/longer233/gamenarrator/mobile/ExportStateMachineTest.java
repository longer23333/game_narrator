package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ExportStateMachineTest {
    @Test public void completesNormalLifecycle() {
        ExportStateMachine machine = new ExportStateMachine();
        machine.prepare();
        machine.run();
        machine.complete();
        assertEquals(ExportStateMachine.State.COMPLETED, machine.state());
        assertTrue(machine.isTerminal());
    }

    @Test public void cancelReachesCancelledTerminal() {
        ExportStateMachine machine = new ExportStateMachine();
        machine.prepare();
        machine.run();
        machine.cancel();
        assertEquals(ExportStateMachine.State.CANCELING, machine.state());
        assertTrue(!machine.isTerminal());
        machine.cancelled();
        assertEquals(ExportStateMachine.State.CANCELLED, machine.state());
        assertTrue(machine.isTerminal());
    }

    @Test public void terminalStateCannotBeOverwritten() {
        ExportStateMachine machine = new ExportStateMachine();
        machine.prepare();
        machine.run();
        machine.fail("disk full");
        assertEquals("disk full", machine.error());
        try {
            machine.complete();
            fail("expected terminal guard");
        } catch (IllegalStateException expected) { }
    }

    @Test public void pauseResumeIsSupported() {
        ExportStateMachine machine = new ExportStateMachine();
        machine.prepare();
        machine.run();
        machine.pause();
        assertEquals(ExportStateMachine.State.PAUSED, machine.state());
        machine.resume();
        assertEquals(ExportStateMachine.State.RUNNING, machine.state());
    }

    @Test public void terminalStateCanResetToIdle() {
        ExportStateMachine machine = new ExportStateMachine();
        machine.prepare();
        machine.run();
        machine.complete();
        machine.reset();
        assertEquals(ExportStateMachine.State.IDLE, machine.state());
        machine.prepare();
        assertEquals(ExportStateMachine.State.PREPARING, machine.state());
    }
}
