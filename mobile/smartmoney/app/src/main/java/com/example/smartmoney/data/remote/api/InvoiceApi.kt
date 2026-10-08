package com.example.smartmoney.data.remote.api

import com.example.smartmoney.domain.model.Invoice
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface InvoiceApi {
    @GET("/api/invoices")
    suspend fun getInvoices(@Query("page") page: Int): Response<List<Invoice>>

    @Multipart
    @POST("/api/invoices")
    suspend fun uploadInvoice(
        @Part file: MultipartBody.Part,
        @Part("vendor") vendor: RequestBody,
        @Part("invoiceNumber") invoiceNumber: RequestBody?,
        @Part("amount") amount: RequestBody,
        @Part("currency") currency: RequestBody,
        @Part("invoiceDate") invoiceDate: RequestBody,
        @Part("dueDate") dueDate: RequestBody?
    ): Response<Invoice>

    @DELETE("/api/invoices/{id}")
    suspend fun deleteInvoice(@Path("id") id: String): Response<Unit>
}
