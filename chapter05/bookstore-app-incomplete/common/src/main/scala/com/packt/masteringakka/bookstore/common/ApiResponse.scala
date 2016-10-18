package com.packt.masteringakka.bookstore.common


/**
 * Representation of a response from a REST api call.  Contains meta data as well as the optional
 * response payload if there was no error
 */
case class ApiResponse[T <: AnyRef](meta:ApiResponseMeta, response:Option[T] = None)

/**
 * Meta data about the response that will contain status code and any error info if there was an error
 */
case class ApiResponseMeta(statusCode:Int, error:Option[ErrorMessage] = None)