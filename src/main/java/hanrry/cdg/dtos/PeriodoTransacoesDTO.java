package hanrry.cdg.dtos;

import java.util.List;

public record PeriodoTransacoesDTO(List<TransacaoResponseDTO> itens, boolean temAnteriores) {}
