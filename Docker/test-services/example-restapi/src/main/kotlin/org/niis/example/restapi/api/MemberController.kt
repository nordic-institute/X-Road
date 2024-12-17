package org.niis.example.restapi.api

import org.niis.example.restapi.core.Member
import org.niis.example.restapi.core.MemberService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {

    @GetMapping
    fun getAllMembers(): List<Member> = memberService.getAllMembers()

    @GetMapping("/{code}")
    fun getMember(@PathVariable code: String): Member = memberService.getMemberByCode(code)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(@RequestBody member: Member) = memberService.addMember(member)

    @PutMapping("/{code}")
    fun deleteMember(@PathVariable code: String, @RequestBody member: Member) = memberService.updateMember(code, member)

    @DeleteMapping("/{code}")
    fun deleteMember(@PathVariable code: String) = memberService.deleteMember(code)

}
