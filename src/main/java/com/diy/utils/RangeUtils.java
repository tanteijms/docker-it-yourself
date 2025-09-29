package com.diy.utils;

import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP Range请求处理工具
 * 
 * @author diy
 */
public class RangeUtils {

    /**
     * Range头格式：bytes=start-end
     */
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");

    /**
     * Content-Range头格式：start-end/total 或 start-end
     */
    private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("(\\d+)-(\\d+)(?:/(\\d+))?");

    /**
     * Range信息
     */
    @Data
    public static class RangeInfo {
        private long start;
        private long end;
        private Long total;
        private boolean valid;

        public RangeInfo(long start, long end) {
            this.start = start;
            this.end = end;
            this.valid = true;
        }

        public RangeInfo(long start, long end, Long total) {
            this.start = start;
            this.end = end;
            this.total = total;
            this.valid = true;
        }

        public static RangeInfo invalid() {
            RangeInfo range = new RangeInfo(0, 0);
            range.valid = false;
            return range;
        }

        public long getLength() {
            return end - start + 1;
        }
    }

    /**
     * 解析Range头
     * 
     * @param rangeHeader   Range头值，例如：bytes=0-1023
     * @param contentLength 内容总长度
     * @return Range信息
     */
    public static RangeInfo parseRange(String rangeHeader, long contentLength) {
        if (rangeHeader == null || rangeHeader.trim().isEmpty()) {
            return RangeInfo.invalid();
        }

        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) {
            return RangeInfo.invalid();
        }

        try {
            long start = Long.parseLong(matcher.group(1));
            String endStr = matcher.group(2);

            long end;
            if (endStr.isEmpty()) {
                // bytes=1000- 表示从1000到文件末尾
                end = contentLength - 1;
            } else {
                end = Long.parseLong(endStr);
            }

            // 验证范围有效性
            if (start < 0 || end < 0 || start > end || start >= contentLength) {
                return RangeInfo.invalid();
            }

            // 确保end不超过内容长度
            if (end >= contentLength) {
                end = contentLength - 1;
            }

            return new RangeInfo(start, end, contentLength);

        } catch (NumberFormatException e) {
            return RangeInfo.invalid();
        }
    }

    /**
     * 解析Content-Range头
     * 
     * @param contentRangeHeader Content-Range头值，例如：0-1023/2048 或 0-1023
     * @return Range信息
     */
    public static RangeInfo parseContentRange(String contentRangeHeader) {
        if (contentRangeHeader == null || contentRangeHeader.trim().isEmpty()) {
            return RangeInfo.invalid();
        }

        Matcher matcher = CONTENT_RANGE_PATTERN.matcher(contentRangeHeader.trim());
        if (!matcher.matches()) {
            return RangeInfo.invalid();
        }

        try {
            long start = Long.parseLong(matcher.group(1));
            long end = Long.parseLong(matcher.group(2));
            String totalStr = matcher.group(3);

            Long total = null;
            if (totalStr != null) {
                total = Long.parseLong(totalStr);
            }

            // 验证范围有效性
            if (start < 0 || end < 0 || start > end) {
                return RangeInfo.invalid();
            }

            if (total != null && (start >= total || end >= total)) {
                return RangeInfo.invalid();
            }

            return new RangeInfo(start, end, total);

        } catch (NumberFormatException e) {
            return RangeInfo.invalid();
        }
    }

    /**
     * 生成Range响应头
     * 
     * @param start 开始位置
     * @param end   结束位置
     * @param total 总长度
     * @return Range响应头值
     */
    public static String buildRangeResponse(long start, long end, long total) {
        return String.format("%d-%d", start, end);
    }

    /**
     * 生成Content-Range头
     * 
     * @param start 开始位置
     * @param end   结束位置
     * @param total 总长度
     * @return Content-Range头值
     */
    public static String buildContentRange(long start, long end, long total) {
        return String.format("bytes %d-%d/%d", start, end, total);
    }

    /**
     * 检查是否是有效的Range请求
     * 
     * @param rangeHeader Range头值
     * @return 是否有效
     */
    public static boolean isValidRangeRequest(String rangeHeader) {
        return rangeHeader != null && RANGE_PATTERN.matcher(rangeHeader.trim()).matches();
    }

    /**
     * 计算Range请求的状态码
     * 
     * @param range         Range信息
     * @param contentLength 内容长度
     * @return HTTP状态码
     */
    public static int getRangeStatusCode(RangeInfo range, long contentLength) {
        if (!range.isValid()) {
            return 416; // Range Not Satisfiable
        }

        if (range.getStart() == 0 && range.getEnd() == contentLength - 1) {
            return 200; // OK (完整内容)
        }

        return 206; // Partial Content
    }
}
