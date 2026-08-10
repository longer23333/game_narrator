package cn.longer233.gamenarrator.mobile;

/**
 * Source of project state used by edit commands. The activity implements this
 * with {@link MobileProjectStore}; tests can use an in-memory implementation.
 */
public interface ProjectState {
    ProjectSnapshot capture();
    void restore(ProjectSnapshot snapshot);
}
