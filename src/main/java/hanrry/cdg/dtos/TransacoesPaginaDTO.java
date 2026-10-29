package hanrry.cdg.dtos;

import java.util.List;

public record TransacoesPaginaDTO(List<TransacaoResponseDTO> itens, boolean temMais) {}
