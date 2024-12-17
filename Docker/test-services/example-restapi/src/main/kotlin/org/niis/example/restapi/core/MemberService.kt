package org.niis.example.restapi.core

import jakarta.annotation.PostConstruct
import org.niis.example.restapi.core.fault.MemberExistsException
import org.niis.example.restapi.core.fault.MemberNotFoundException
import org.springframework.stereotype.Service

@Service
class MemberService {

    val members: MutableMap<String, Member> = mutableMapOf()

    fun getAllMembers(): List<Member> = members.values.toList()

    fun getMemberByCode(code: String): Member = members[code] ?: throw MemberNotFoundException("Member with code '$code' not found")

    fun addMember(member: Member) {
        if (members.contains(member.code)) {
            throw MemberExistsException("Member with code '${member.code}' already exists")
        }
        members[member.code] = member
    }

    fun updateMember(code: String, member: Member) {
        members[code]?.let {
            members[code] = member.copy(code = code)
        } ?: throw MemberNotFoundException("Member with code '$code' not found")
    }

    fun deleteMember(code: String) = members.remove(code)

    @PostConstruct
    fun init() {
        addMember(Member("80419486", "MTÜ Nordic Institute for Interoperability Solutions", MemberClass.NGO))
    }
}
