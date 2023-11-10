package de.timesnake.web.printserver.data.entity;

import jakarta.persistence.*;

@Entity(name = "printer")
public class Printer {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "priority", nullable = false, unique = true)
  private Integer priority;

  @Column(name = "cups_name", nullable = false)
  private String cupsName;

  @Column(name = "price_one_sided", nullable = false)
  private Double priceOneSided;

  @Column(name = "price_two_sided", nullable = false)
  private Double priceTwoSided;

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public Double getPriceTwoSided() {
    return priceTwoSided;
  }

  public void setPriceTwoSided(Double priceTwoSided) {
    this.priceTwoSided = priceTwoSided;
  }

  public Double getPriceOneSided() {
    return priceOneSided;
  }

  public void setPriceOneSided(Double priceOneSided) {
    this.priceOneSided = priceOneSided;
  }

  public String getCupsName() {
    return cupsName;
  }

  public void setCupsName(String cupsName) {
    this.cupsName = cupsName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }
}
