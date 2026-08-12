package cn.longer233.gamenarrator.common;

/** Deliberately hides whether a resource exists for another owner. */
public class OwnedResourceNotFoundException extends RuntimeException {
    public OwnedResourceNotFoundException() { super("Resource not found"); }
}
