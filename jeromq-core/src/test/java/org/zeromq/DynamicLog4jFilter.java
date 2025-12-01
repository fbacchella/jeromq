package org.zeromq;

import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

@Plugin(name = "DynamicFilter", category = Core.CATEGORY_NAME, elementType = Filter.ELEMENT_TYPE)
public class DynamicLog4jFilter extends AbstractFilter
{
    private final boolean inCircleCI;
    private final boolean explicitDeactivation;

    public DynamicLog4jFilter() {
        inCircleCI = Optional.ofNullable(System.getenv("CIRCLECI")).map(Boolean::valueOf).orElse(false);
        explicitDeactivation = Optional.ofNullable(System.getProperty("maven.logging.skip")).map(Boolean::valueOf).orElse(false);
    }

    @Override
    public Result filter(LogEvent event)
    {
        if (event.getLoggerName().startsWith("org.junit"))
        {
            return Result.ACCEPT;
        }
        else if ((inCircleCI || explicitDeactivation) && event.getLevel().isLessSpecificThan(Level.ERROR))
        {
            return Result.DENY;
        } else {
            return Result.ACCEPT;
        }
    }

    @PluginFactory
    public static DynamicLog4jFilter createFilter()
    {
       return new DynamicLog4jFilter();
    }
}
