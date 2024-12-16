package org.niis.example.restapi.core

data class Member(
    val code: String,
    val name: String,
    val memberClass: MemberClass
)

enum class MemberClass {
    COM,
    GOV,
    NEE,
    NGO
}
