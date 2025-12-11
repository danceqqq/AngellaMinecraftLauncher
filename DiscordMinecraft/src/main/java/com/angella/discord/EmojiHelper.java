package com.angella.discord;

/**
 * Helper class for converting numbers to custom Discord emoji
 */
public class EmojiHelper {
    private static final String[] DIGIT_EMOJIS = {
        "<:00:1445295647996645418>", // 0
        "<:01:1445295678808002611>", // 1
        "<:02:1445295731014369351>", // 2
        "<:03:1445295761284796436>", // 3
        "<:04:1445295788363350087>", // 4
        "<:05:1445295816460996629>", // 5
        "<:06:1445295847716683838>", // 6
        "<:07:1445295878004015216>", // 7
        "<:08:1445295906810232852>", // 8
        "<:09:1445295940624715807>"  // 9
    };
    
    // Blue dash separator emoji
    private static final String BLUE_DASH_EMOJI = "<:blue_dash:1445748743646347367>";
    
    /**
     * Returns a separator line made of 12 blue dash emojis
     */
    public static String getSeparatorLine() {
        return BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + 
               BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + 
               BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + BLUE_DASH_EMOJI + BLUE_DASH_EMOJI;
    }
    
    /**
     * Converts a number to emoji representation
     */
    public static String numberToEmoji(int number) {
        if (number < 0) {
            return "-" + numberToEmoji(-number);
        }
        if (number == 0) {
            return DIGIT_EMOJIS[0];
        }
        
        StringBuilder result = new StringBuilder();
        while (number > 0) {
            result.insert(0, DIGIT_EMOJIS[number % 10]);
            number /= 10;
        }
        return result.toString();
    }
    
    /**
     * Converts a string containing numbers to emoji representation
     * Handles decimal numbers and regular integers
     */
    public static String replaceNumbersInText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Replace numbers in the text, including decimal numbers
        StringBuilder result = new StringBuilder();
        StringBuilder currentNumber = new StringBuilder();
        boolean hasDecimal = false;
        
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                currentNumber.append(c);
            } else if (c == '.' && currentNumber.length() > 0) {
                // Handle decimal point
                currentNumber.append(c);
                hasDecimal = true;
            } else {
                if (currentNumber.length() > 0) {
                    String numStr = currentNumber.toString();
                    if (hasDecimal) {
                        // Handle decimal number
                        String[] parts = numStr.split("\\.");
                        if (parts.length == 2) {
                            result.append(numberToEmoji(Integer.parseInt(parts[0])));
                            result.append(".");
                            result.append(numberToEmoji(Integer.parseInt(parts[1])));
                        } else {
                            result.append(numStr); // Fallback
                        }
                    } else {
                        result.append(numberToEmoji(Integer.parseInt(numStr)));
                    }
                    currentNumber.setLength(0);
                    hasDecimal = false;
                }
                result.append(c);
            }
        }
        
        // Handle number at the end
        if (currentNumber.length() > 0) {
            String numStr = currentNumber.toString();
            if (hasDecimal) {
                String[] parts = numStr.split("\\.");
                if (parts.length == 2) {
                    result.append(numberToEmoji(Integer.parseInt(parts[0])));
                    result.append(".");
                    result.append(numberToEmoji(Integer.parseInt(parts[1])));
                } else {
                    result.append(numStr);
                }
            } else {
                result.append(numberToEmoji(Integer.parseInt(numStr)));
            }
        }
        
        return result.toString();
    }
}

