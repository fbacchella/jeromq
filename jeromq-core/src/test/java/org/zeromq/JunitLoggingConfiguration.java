package org.zeromq;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilterable;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Logging is configured too early in the junit lifecycle, so we need to configure it here.
 */
public class JunitLoggingConfiguration implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context)
    {
        LoggerContext lctx = (LoggerContext) LogManager.getContext(false);
        Configuration conf = lctx.getConfiguration();
        AbstractFilterable filterable = conf.getAppender("Console");
        filterable.addFilter(new DynamicLog4jFilter());
    }
}
