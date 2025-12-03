package org.zeromq;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilterable;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Logging is configured too early in the junit lifecycle, so an arbitrary extension is defined.
 * When JUnit resolves the class, logging is reconfigured.
 */
public class JunitLoggingReconfigurator implements BeforeAllCallback {
    static
    {
        LoggerContext lctx = (LoggerContext) LogManager.getContext(false);
        Configuration conf = lctx.getConfiguration();
        AbstractFilterable filterable = conf.getAppender("Console");
        filterable.addFilter(new DynamicLog4jFilter());
    }

    @Override
    public void beforeAll(ExtensionContext context)
    {
        // Nothing to do, instantiate the extension was enough
    }
}
