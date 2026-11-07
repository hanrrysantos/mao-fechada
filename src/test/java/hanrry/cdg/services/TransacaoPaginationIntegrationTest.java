package hanrry.cdg.services;

import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.Transacao;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TransacaoPaginationIntegrationTest {
    @Autowired private TransacaoService service;
    @Autowired private UserRepository users;
    @Autowired private CategoriaRepository categorias;
    @Autowired private TransacaoRepository transacoes;

    @Test
    void paginaOrdenadaEPeriodoRespeitamOUsuario() {
        User dono = new User();
        dono.setNome("Dono");
        dono = users.saveAndFlush(dono);
        User outro = new User();
        outro.setNome("Outro");
        outro = users.saveAndFlush(outro);
        Categoria categoriaDono = categoria(dono);
        Categoria categoriaOutro = categoria(outro);
        Transacao antiga = transacao(dono, categoriaDono, LocalDate.of(2026, 10, 1));
        Transacao media = transacao(dono, categoriaDono, LocalDate.of(2026, 10, 5));
        Transacao nova = transacao(dono, categoriaDono, LocalDate.of(2026, 10, 6));
        Transacao alheia = transacao(outro, categoriaOutro, LocalDate.of(2026, 10, 7));

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(dono.getId().toString(), null));

            var primeira = service.listar(0, 2);
            var segunda = service.listar(1, 2);
            assertEquals(List.of(nova.getId(), media.getId()), primeira.itens().stream().map(item -> item.getId()).toList());
            assertTrue(primeira.temMais());
            assertEquals(List.of(antiga.getId()), segunda.itens().stream().map(item -> item.getId()).toList());
            assertEquals(nova.getId(), service.buscar(nova.getId()).getId());
            assertThrows(ResourceNotFoundException.class, () -> service.buscar(alheia.getId()));

            var periodo = service.listarPeriodo(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
            assertEquals(List.of(nova.getId(), media.getId()), periodo.itens().stream().map(item -> item.getId()).toList());
            assertTrue(periodo.temAnteriores());
        } finally {
            SecurityContextHolder.clearContext();
            transacoes.deleteAll(List.of(antiga, media, nova, alheia));
            categorias.deleteAll(List.of(categoriaDono, categoriaOutro));
            users.deleteAll(List.of(dono, outro));
        }
    }

    private Categoria categoria(User user) {
        Categoria categoria = new Categoria();
        categoria.setNome("Mercado");
        categoria.setTipo(TipoTransacao.DESPESA);
        categoria.setUser(user);
        return categorias.saveAndFlush(categoria);
    }

    private Transacao transacao(User user, Categoria categoria, LocalDate data) {
        Transacao transacao = new Transacao();
        transacao.setUser(user);
        transacao.setCategoria(categoria);
        transacao.setData(data);
        transacao.setDescricao("Compra");
        transacao.setValor(new BigDecimal("10.00"));
        transacao.setTipo(TipoTransacao.DESPESA);
        return transacoes.saveAndFlush(transacao);
    }
}
