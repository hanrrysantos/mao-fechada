package hanrry.cdg.services;

import hanrry.cdg.dtos.DashboardResponseDTO;
import hanrry.cdg.dtos.ResumoMensalResponseDTO;
import hanrry.cdg.dtos.PeriodoTransacoesDTO;
import hanrry.cdg.dtos.TransacaoRequestDTO;
import hanrry.cdg.dtos.TransacaoResponseDTO;
import hanrry.cdg.dtos.TransacoesPaginaDTO;
import hanrry.cdg.exceptions.AccessDeniedException;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.TransacaoMapper;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.Transacao;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TotalPorCategoria;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
@Service
public class TransacaoService {

    private final TransacaoRepository repository;
    private final CategoriaRepository categoriaRepository;
    private final UserRepository userRepository;
    private final TransacaoMapper mapper;

    public TransacaoService(TransacaoRepository repository, CategoriaRepository categoriaRepository, UserRepository userRepository, TransacaoMapper mapper) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public TransacaoResponseDTO criar(TransacaoRequestDTO dto) {
        validar(dto);
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        Categoria categoria = buscarCategoria(dto.getCategoriaId(), user);

        Transacao transacao = mapper.toEntity(dto);
        transacao.setUser(user);
        transacao.setCategoria(categoria);
        transacao.setTipo(categoria.getTipo());

        Transacao salva = repository.save(transacao);
        return mapper.toDto(salva);
    }

    public TransacaoResponseDTO atualizar(Long id, TransacaoRequestDTO dto) {
        validar(dto);
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();

        Transacao transacao = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada."));

        if (!transacao.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Você não tem permissão para editar este lançamento.");
        }

        Categoria categoria = buscarCategoria(dto.getCategoriaId(), user);
        mapper.atualizar(dto, transacao);
        transacao.setCategoria(categoria);
        transacao.setTipo(categoria.getTipo());

        Transacao salva = repository.save(transacao);
        return mapper.toDto(salva);
    }

    private static void validar(TransacaoRequestDTO dto) {
        if (dto.getValor() == null || dto.getValor().signum() <= 0 || dto.getValor().stripTrailingZeros().scale() > 2
                || dto.getValor().precision() - dto.getValor().scale() > 17) {
            throw new BusinessRuleException("O valor deve ser positivo, ter no máximo duas casas decimais e caber no limite monetário.");
        }
        if (dto.getCategoriaId() == null || dto.getData() == null || dto.getDescricao() == null || dto.getDescricao().isBlank()) {
            throw new BusinessRuleException("Preencha descrição, data e categoria.");
        }
        if (dto.getDescricao().length() > 255) {
            throw new BusinessRuleException("A descrição deve ter no máximo 255 caracteres.");
        }
    }

    private Categoria buscarCategoria(Long categoriaId, User user) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com ID: " + categoriaId));

        if (!categoria.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Você não tem permissão para utilizar ou visualizar este recurso.");
        }
        return categoria;
    }

    private static final Sort ORDEM_RECENTE = Sort.by(Sort.Direction.DESC, "data", "id");

    public TransacoesPaginaDTO listar(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 50) {
            throw new BusinessRuleException("Informe página válida e tamanho entre 1 e 50.");
        }
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        var resultado = repository.findByUserId(userId, PageRequest.of(pagina, tamanho, ORDEM_RECENTE));
        return new TransacoesPaginaDTO(resultado.getContent().stream().map(mapper::toDto).toList(), resultado.hasNext());
    }

    public TransacaoResponseDTO buscar(Long id) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return repository.findByIdAndUserId(id, userId).map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada."));
    }

    public PeriodoTransacoesDTO listarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || inicio.isAfter(fim) || ChronoUnit.DAYS.between(inicio, fim) > 30) {
            throw new BusinessRuleException("Informe um período válido de até 31 dias.");
        }
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        var itens = repository.findByUserIdAndDataBetween(userId, inicio, fim, ORDEM_RECENTE).stream()
                .map(mapper::toDto).toList();
        return new PeriodoTransacoesDTO(itens, repository.existsByUserIdAndDataBefore(userId, inicio));
    }

    public DashboardResponseDTO getDashboard() {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        BigDecimal totalReceitas = repository.totalPorTipo(userId, TipoTransacao.RECEITA);
        BigDecimal totalDespesas = repository.totalPorTipo(userId, TipoTransacao.DESPESA);

        BigDecimal saldo = totalReceitas.subtract(totalDespesas);

        return new DashboardResponseDTO(totalReceitas, totalDespesas, saldo);
    }

    public ResumoMensalResponseDTO getResumoMensal(String mes) {
        YearMonth periodo;
        try {
            periodo = mes == null || mes.isBlank() ? YearMonth.now(ZoneId.of("America/Sao_Paulo")) : YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("Informe o mês no formato AAAA-MM.");
        }
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        LocalDate inicio = periodo.atDay(1);
        LocalDate fim = periodo.atEndOfMonth();
        YearMonth anterior = periodo.minusMonths(1);

        BigDecimal receitas = centavos(repository.totalPorTipo(userId, TipoTransacao.RECEITA, inicio, fim));
        BigDecimal despesas = centavos(repository.totalPorTipo(userId, TipoTransacao.DESPESA, inicio, fim));
        BigDecimal saldoAcumulado = centavos(repository.totalPorTipo(userId, TipoTransacao.RECEITA)
                .subtract(repository.totalPorTipo(userId, TipoTransacao.DESPESA)));

        Map<Long, TotalPorCategoria> totais = repository.totalPorCategoria(userId, inicio, fim).stream()
                .collect(Collectors.toMap(TotalPorCategoria::categoriaId, Function.identity()));
        List<ResumoMensalResponseDTO.Categoria> categorias = categoriaRepository.findByUserId(userId).stream()
                .map(categoria -> {
                    TotalPorCategoria total = totais.get(categoria.getId());
                    return new ResumoMensalResponseDTO.Categoria(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                            centavos(total == null ? BigDecimal.ZERO : total.total()), total == null ? 0 : total.quantidade(),
                            categoria.getMetaMensal());
                })
                .sorted(Comparator.comparing(ResumoMensalResponseDTO.Categoria::total).reversed()
                        .thenComparing(ResumoMensalResponseDTO.Categoria::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ResumoMensalResponseDTO(
                periodo.toString(),
                receitas,
                despesas,
                receitas.subtract(despesas),
                centavos(repository.totalPorTipo(userId, TipoTransacao.RECEITA, anterior.atDay(1), anterior.atEndOfMonth())),
                centavos(repository.totalPorTipo(userId, TipoTransacao.DESPESA, anterior.atDay(1), anterior.atEndOfMonth())),
                saldoAcumulado,
                totais.values().stream().mapToLong(TotalPorCategoria::quantidade).sum(),
                categorias);
    }

    private static BigDecimal centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    public void deletar(Long id) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();

        Transacao transacao = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada."));

        if (!transacao.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Você não tem permissão para excluir este lançamento.");
        }

        repository.delete(transacao);
    }
}
