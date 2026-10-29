package hanrry.cdg.services;

import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.TransacaoPendenteTelegram;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TransacaoPendenteTelegramRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TelegramConfirmationConcurrencyTest {
    @Autowired private TelegramTransactionService service;
    @Autowired private UserRepository users;
    @Autowired private CategoriaRepository categorias;
    @Autowired private TransacaoRepository transacoes;
    @Autowired private TransacaoPendenteTelegramRepository pendentes;

    @Test
    void confirmaUmaUnicaVezQuandoDuasChamadasChegamJuntas() throws Exception {
        long telegramId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        User user = new User();
        user.setNome("Teste concorrente");
        user.setTelegramUserId(telegramId);
        user = users.saveAndFlush(user);

        Categoria categoria = new Categoria();
        categoria.setNome("Mercado");
        categoria.setTipo(TipoTransacao.DESPESA);
        categoria.setUser(user);
        categoria = categorias.saveAndFlush(categoria);

        TransacaoPendenteTelegram pendente = new TransacaoPendenteTelegram();
        pendente.setId(UUID.randomUUID().toString());
        pendente.setTelegramUserId(telegramId);
        pendente.setValor(new BigDecimal("32.50"));
        pendente.setDescricao("Almoço");
        pendente.setData(LocalDate.now());
        pendente.setCategoriaId(categoria.getId());
        pendente.setCategoriaNome(categoria.getNome());
        pendente.setTipo(TipoTransacao.DESPESA);
        pendente.setExpiraEm(Instant.now().plusSeconds(600));
        pendentes.saveAndFlush(pendente);

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch pronto = new CountDownLatch(2);
            CountDownLatch inicio = new CountDownLatch(1);
            String id = pendente.getId();
            java.util.concurrent.Callable<String> confirmar = () -> {
                pronto.countDown();
                inicio.await();
                return service.confirmar(id, telegramId);
            };
            Future<String> primeira = executor.submit(confirmar);
            Future<String> segunda = executor.submit(confirmar);
            pronto.await(5, TimeUnit.SECONDS);
            inicio.countDown();
            String resposta1 = primeira.get(10, TimeUnit.SECONDS);
            String resposta2 = segunda.get(10, TimeUnit.SECONDS);

            assertEquals(1, transacoes.findByUserId(user.getId()).size());
            assertEquals(1, (resposta1.startsWith("✅") ? 1 : 0) + (resposta2.startsWith("✅") ? 1 : 0));
        } finally {
            pendentes.deleteById(pendente.getId());
            transacoes.deleteAll(transacoes.findByUserId(user.getId()));
            categorias.deleteById(categoria.getId());
            users.deleteById(user.getId());
        }
    }
}
