package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfiguracionRepository extends JpaRepository<Configuracion, String> {

    Optional<Configuracion> findByClave(String clave);

    List<Configuracion> findAllByOrderByClaveAsc();

    default String getValor(String clave, String defaultValue) {
        return findByClave(clave).map(Configuracion::getValor).orElse(defaultValue);
    }

    default int getInt(String clave, int defaultValue) {
        try {
            return Integer.parseInt(getValor(clave, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default boolean getBoolean(String clave, boolean defaultValue) {
        return Boolean.parseBoolean(getValor(clave, String.valueOf(defaultValue)));
    }
}