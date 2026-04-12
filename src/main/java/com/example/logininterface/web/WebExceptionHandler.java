package com.example.logininterface.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.List;

@ControllerAdvice
public class WebExceptionHandler {

    private static final String DEFAULT_REDIRECT_PATH = "/profile";

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceeded(
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        String message = "上传失败：文件大小超出限制。当前单个文件上限 20MB，本次总上传上限 200MB，请减少数量或压缩图片后重试。";
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AdminController.BackgroundUploadResponse(
                    false,
                    message,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    resolveRedirectPath(request)
            ));
        }
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:" + resolveRedirectPath(request);
    }

    @ExceptionHandler(MultipartException.class)
    public Object handleMultipartException(
            MultipartException exception,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        String message = "上传失败：文件数据读取异常，请重新选择图片后重试。";
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AdminController.BackgroundUploadResponse(
                    false,
                    message,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    resolveRedirectPath(request)
            ));
        }
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:" + resolveRedirectPath(request);
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
    }

    private String resolveRedirectPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return DEFAULT_REDIRECT_PATH;
        }

        try {
            String path = URI.create(referer).getPath();
            if (path == null || path.isBlank() || "/error".equals(path)) {
                return DEFAULT_REDIRECT_PATH;
            }
            return path;
        } catch (IllegalArgumentException exception) {
            return DEFAULT_REDIRECT_PATH;
        }
    }
}
