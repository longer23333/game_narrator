package cn.longer233.gamenarrator.admin;
public class AdminAccessDeniedException extends RuntimeException { public AdminAccessDeniedException() { super("需要已登录的管理员账号"); } }
