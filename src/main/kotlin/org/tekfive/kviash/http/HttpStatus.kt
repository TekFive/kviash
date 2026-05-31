package org.tekfive.kviash.http

class HttpStatus(val code: Int, val reason: String) {
    companion object {
        // 1xx Informational
        val Continue = HttpStatus(100, "Continue") // "continue" is a keyword
        val SwitchingProtocols = HttpStatus(101, "Switching Protocols")
        val Processing = HttpStatus(102, "Processing")
        val EarlyHints = HttpStatus(103, "Early Hints")

        // 2xx Success
        val Ok = HttpStatus(200, "OK")
        val Created = HttpStatus(201, "Created")
        val Accepted = HttpStatus(202, "Accepted")
        val NonAuthoritativeInformation = HttpStatus(203, "Non-Authoritative Information")
        val NoContent = HttpStatus(204, "No Content")
        val ResetContent = HttpStatus(205, "Reset Content")
        val PartialContent = HttpStatus(206, "Partial Content")
        val MultiStatus = HttpStatus(207, "Multi-Status")
        val AlreadyReported = HttpStatus(208, "Already Reported")
        val ImUsed = HttpStatus(226, "IM Used")

        // 3xx Redirection
        val MultipleChoices = HttpStatus(300, "Multiple Choices")
        val MovedPermanently = HttpStatus(301, "Moved Permanently")
        val Found = HttpStatus(302, "Found")
        val SeeOther = HttpStatus(303, "See Other")
        val NotModified = HttpStatus(304, "Not Modified")
        val UseProxy = HttpStatus(305, "Use Proxy")
        val Unused306 = HttpStatus(306, "Unused")
        val TemporaryRedirect = HttpStatus(307, "Temporary Redirect")
        val PermanentRedirect = HttpStatus(308, "Permanent Redirect")

        // 4xx Client Error
        val BadRequest = HttpStatus(400, "Bad Request")
        val Unauthorized = HttpStatus(401, "Unauthorized")
        val PaymentRequired = HttpStatus(402, "Payment Required")
        val Forbidden = HttpStatus(403, "Forbidden")
        val NotFound = HttpStatus(404, "Not Found")
        val MethodNotAllowed = HttpStatus(405, "Method Not Allowed")
        val NotAcceptable = HttpStatus(406, "Not Acceptable")
        val ProxyAuthenticationRequired = HttpStatus(407, "Proxy Authentication Required")
        val RequestTimeout = HttpStatus(408, "Request Timeout")
        val Conflict = HttpStatus(409, "Conflict")
        val Gone = HttpStatus(410, "Gone")
        val LengthRequired = HttpStatus(411, "Length Required")
        val PreconditionFailed = HttpStatus(412, "Precondition Failed")
        val ContentTooLarge = HttpStatus(413, "Content Too Large")
        val UriTooLong = HttpStatus(414, "URI Too Long")
        val UnsupportedMediaType = HttpStatus(415, "Unsupported Media Type")
        val RangeNotSatisfiable = HttpStatus(416, "Range Not Satisfiable")
        val ExpectationFailed = HttpStatus(417, "Expectation Failed")
        val ImATeapot = HttpStatus(418, "I'm a teapot")
        val MisdirectedRequest = HttpStatus(421, "Misdirected Request")
        val UnprocessableEntity = HttpStatus(422, "Unprocessable Entity")
        val Locked = HttpStatus(423, "Locked")
        val FailedDependency = HttpStatus(424, "Failed Dependency")
        val TooEarly = HttpStatus(425, "Too Early")
        val UpgradeRequired = HttpStatus(426, "Upgrade Required")
        val PreconditionRequired = HttpStatus(428, "Precondition Required")
        val TooManyRequests = HttpStatus(429, "Too Many Requests")
        val RequestHeaderFieldsTooLarge = HttpStatus(431, "Request Header Fields Too Large")
        val UnavailableForLegalReasons = HttpStatus(451, "Unavailable For Legal Reasons")

        // 5xx Server Error
        val InternalServerError = HttpStatus(500, "Internal Server Error")
        val NotImplemented = HttpStatus(501, "Not Implemented")
        val BadGateway = HttpStatus(502, "Bad Gateway")
        val ServiceUnavailable = HttpStatus(503, "Service Unavailable")
        val GatewayTimeout = HttpStatus(504, "Gateway Timeout")
        val HttpVersionNotSupported = HttpStatus(505, "HTTP Version Not Supported")
        val VariantAlsoNegotiates = HttpStatus(506, "Variant Also Negotiates")
        val InsufficientStorage = HttpStatus(507, "Insufficient Storage")
        val LoopDetected = HttpStatus(508, "Loop Detected")
        val NotExtended = HttpStatus(510, "Not Extended")
        val NetworkAuthenticationRequired = HttpStatus(511, "Network Authentication Required")

        private val codeMap = listOf(
            Continue,
            SwitchingProtocols,
            Processing,
            EarlyHints,
            Ok,
            Created,
            Accepted,
            NonAuthoritativeInformation,
            NoContent,
            ResetContent,
            PartialContent,
            MultiStatus,
            AlreadyReported,
            ImUsed,
            MultipleChoices,
            MovedPermanently,
            Found,
            SeeOther,
            NotModified,
            UseProxy,
            Unused306,
            TemporaryRedirect,
            PermanentRedirect,
            BadRequest,
            Unauthorized,
            PaymentRequired,
            Forbidden,
            NotFound,
            MethodNotAllowed,
            NotAcceptable,
            ProxyAuthenticationRequired,
            RequestTimeout,
            Conflict,
            Gone,
            LengthRequired,
            PreconditionFailed,
            ContentTooLarge,
            UriTooLong,
            UnsupportedMediaType,
            RangeNotSatisfiable,
            ExpectationFailed,
            ImATeapot,
            MisdirectedRequest,
            UnprocessableEntity,
            Locked,
            FailedDependency,
            TooEarly,
            UpgradeRequired,
            PreconditionRequired,
            TooManyRequests,
            RequestHeaderFieldsTooLarge,
            UnavailableForLegalReasons,
            InternalServerError,
            NotImplemented,
            BadGateway,
            ServiceUnavailable,
            GatewayTimeout,
            HttpVersionNotSupported,
            VariantAlsoNegotiates,
            InsufficientStorage,
            LoopDetected,
            NotExtended,
            NetworkAuthenticationRequired
        ).associateBy { it.code }

        fun fromCode(code: Int): HttpStatus? = codeMap[code]

        fun values(): Collection<HttpStatus> = codeMap.values
    }
}