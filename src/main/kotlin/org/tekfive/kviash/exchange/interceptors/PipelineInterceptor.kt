package org.tekfive.kviash.exchange.interceptors

import org.tekfive.kviash.exchange.Exchange

interface PipelineInterceptor {
    /**
     * If provided routePipelineExecution parameter must be invoked in implementation code else the
     * route pipeline will end prematurely.
     */
    fun intercept(exchange: Exchange, continuePipeline:(Exchange) -> (Unit))
}