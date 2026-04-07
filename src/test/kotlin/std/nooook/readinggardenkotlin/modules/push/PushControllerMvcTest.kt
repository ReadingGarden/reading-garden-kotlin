package std.nooook.readinggardenkotlin.modules.push

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class PushControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var pushService: PushService

    @Test
    fun `get push settings should return legacy success envelope`() {
        val pushTime = LocalDateTime.of(2026, 4, 6, 12, 0, 0)
        given(pushService.getPush(1))
            .willReturn(
                PushResponse(
                    push_app_ok = true,
                    push_book_ok = false,
                    push_time = pushTime,
                ),
            )

        mockMvc.perform(
            get("/api/v1/push/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("푸시 알림 조회 성공"))
            .andExpect(jsonPath("$.data.push_app_ok").value(true))
            .andExpect(jsonPath("$.data.push_book_ok").value(false))
            .andExpect(jsonPath("$.data.push_time").exists())
    }

    @Test
    fun `update push should return legacy success envelope and bind nullable fields`() {
        mockMvc.perform(
            put("/api/v1/push/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push_app_ok":true,"push_book_ok":null,"push_time":null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("푸시 알림 수정 성공"))

        verify(pushService).updatePush(1, true, null, null)
    }

    @Test
    fun `send book push should return legacy success envelope`() {
        given(pushService.sendBookPush())
            .willReturn(
                listOf(
                    mapOf(
                        "user_no" to 1,
                        "result" to "sent",
                    ),
                ),
            )

        mockMvc.perform(
            post("/api/v1/push/book")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("독서 알림 푸시 전송 성공"))
            .andExpect(jsonPath("$.data[0].user_no").value(1))
            .andExpect(jsonPath("$.data[0].result").value("sent"))

        verify(pushService).sendBookPush()
    }

    @Test
    fun `send notice push should return legacy success envelope`() {
        given(pushService.sendNoticePush("공지사항 내용"))
            .willReturn(
                listOf(
                    mapOf(
                        "content" to "공지사항 내용",
                        "result" to "sent",
                    ),
                ),
            )

        mockMvc.perform(
            post("/api/v1/push/notice")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .param("content", "공지사항 내용")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("공지사항 푸시 전송 성공"))
            .andExpect(jsonPath("$.data[0].content").value("공지사항 내용"))
            .andExpect(jsonPath("$.data[0].result").value("sent"))

        verify(pushService).sendNoticePush("공지사항 내용")
    }
}
