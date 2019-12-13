package com.pagantis.singer.flows

case class InvalidRequestException(message: String = "Invalid request representation https://github.com/digitalorigin/batch-http/issues/17") extends Exception(message)