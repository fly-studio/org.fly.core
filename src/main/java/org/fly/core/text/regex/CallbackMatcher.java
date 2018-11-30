package org.fly.core.text.regex;

import java.util.regex.*;

public class CallbackMatcher {
    public interface Callback
    {
        String foundMatch(MatchResult matchResult);
    }

    private final Pattern pattern;

    public CallbackMatcher(String regex)
    {
        this.pattern = Pattern.compile(regex);
    }

    public void findMatches(String string, Callback callback)
    {
        final Matcher matcher = this.pattern.matcher(string);
        while(matcher.find())
        {
            callback.foundMatch(matcher.toMatchResult());
        }
    }

    public String replaceMatches(String string, Callback callback)
    {
        final Matcher matcher = this.pattern.matcher(string);

        StringBuilder newString = new StringBuilder();
        int pos = 0;
        while(matcher.find())
        {
            final MatchResult matchResult = matcher.toMatchResult();
            final String replacement = callback.foundMatch(matchResult);
            newString.append(string, pos, matchResult.start())
                    .append(replacement);

            pos = matchResult.end();
        }

        newString.append(string.substring(pos));

        return newString.toString();
    }
}